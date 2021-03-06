/*
 * Licensed to Think Big Analytics, Inc. under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  Think Big Analytics, Inc. licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * Copyright 2012 Think Big Analytics. All Rights Reserved.
 */
package tap;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.JobConf;

import tap.core.InfeasiblePlanException;
import tap.core.PipePlan;
import tap.core.TapAlerter;
import tap.core.TapInterface;
import tap.core.mapreduce.input.TapfileRecordReader;
import tap.util.TapAlerterInterface;

public class Tap implements TapInterface {
    private List<Pipe> writes;
    private int parallelPhases = 2; // default to 2 phases at once - use
                                    // concurrency (also speeds up local
                                    // running)
    private JobConf baseConf = new JobConf();
    private TapAlerterInterface alerter = new TapAlerter();
    private String name = "";
    private CommandOptions options;

    List<Phase> phases = new LinkedList<Phase>();
    int globalPhaseID = 0;

	private Tap() {
    	Class<?> jarClass = this.getClass();
		baseConf.setJarByClass(jarClass);
		init();
    }

    public Tap(CommandOptions o) {
    	options = o;
    	init();
    	options.parse(this);
    	this.named(o.program); // by default set the Tap name.
    }
    
    //TODO: Integrate into framework or remove
    public Tap(Class<?> jarClass, CommandOptions o) {
    	options = o;
        baseConf.setJarByClass(jarClass);
        init();
        options.parse(this);
    }

	/**
	 * @throws RuntimeException
	 */
	private void init() throws RuntimeException {
		baseConf.set("mapred.job.reuse.jvm.num.tasks", "-1");
        try {
            FileSystem fs = FileSystem.get(new URI("/"), baseConf);
            FileSystem localfs = FileSystem.getLocal(baseConf);
            if (fs.equals(localfs)) {
                baseConf.setNumReduceTasks(2); // run only 2 reducers for local
            } else {
                baseConf.setNumReduceTasks(32); // default to 32 reducers - need
                                                // to tune this
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
	}
	
	@Override
	public Phase createPhase() {
		Phase phase = new Phase(this);
		phases.add(phase);
		return phase;
	}

	/**
	 * Setup the result / error alerter for the jobs
	 * @param alerter The alerter to use
	 * @return The Tap
	 */
	public Tap alerter(TapAlerterInterface alerter) {
		this.alerter = alerter;
		return this;
	}
	
	TapAlerterInterface getAlerter() {
		return alerter;
	}

    public Tap produces(List<Pipe> outputs) {
        if (writes == null) {
            writes = new ArrayList<Pipe>(outputs);
        } else {
            writes.addAll(outputs);
        }
        return this;
    }

    public Tap produces(Pipe... outputs) {
        return produces(Arrays.asList(outputs));
    }

    public Tap named(String name) {
        this.name = name;
        return this;
    }

    Tap forceRebuild() {
        options.forceRebuild = true;
        return this;
    }

	public Tap parallelPhases(int parallelPhases) {
        this.parallelPhases = parallelPhases;
        return this;
    }

    public int getParallelPhases() {
        return parallelPhases;
    }
    
    

    /**
     * Runs the plan from the Tap interface
     * @throws InfeasiblePlanException
     */
    void execute() throws InfeasiblePlanException {
        List<PhaseError> result = new ArrayList<PhaseError>();
        PipePlan plan = generatePlan(result);
        if (result.isEmpty()) {
            plan = optimize(plan, result);
            if (result.isEmpty()) {
                execute(plan, result);
            }
        }
        respond(result);
    }

    private void respond(List<PhaseError> result) {
        if (!result.isEmpty()) {
            alerter.alert(result);
        } else {
            alerter.pipeCompletion(getName(),
                    "Execution completed successfully.");
        }
    }

    private List<PhaseError> execute(final PipePlan plan,
            List<PhaseError> errors) {
        ExecutorService execution = Executors
                .newFixedThreadPool(parallelPhases);
        submit(plan, execution, Collections.synchronizedList(errors));

        try {
            synchronized (plan) {
                while (!plan.isComplete() && errors.isEmpty()) {
                    plan.wait();
                }
            }
            execution.shutdown();
            execution.awaitTermination(20L, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            System.err.println("interrupted job");
            alerter.alert("pipe execution interrupted");
        }
        return errors;
    }

    int submission = 0;

    private List<PhaseError> submit(final PipePlan plan,
            final ExecutorService execution, final List<PhaseError> errors) {
        List<Phase> next = plan.getNextProcesses();
        if (next != null) {
            for (final Phase process : next) {
                if (plan.executing(process)) {
                    execution.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                PhaseError error = process.submit();
                                if (error != null) {
                                    errors.add(error);
                                    // alert immediately
                                    System.err.println("Phase failed: "
                                            + error.getMessage());
                                    plan.failed(process);
                                } else {
                                    plan.updated(process);
                                    plan.plan();
                                    submit(plan, execution, Collections
                                            .synchronizedList(errors));
                                }
                            } finally {
                                synchronized (plan) {
                                    plan.notify();
                                }
                            }
                        }

                    });
                }
            }
        }
        return errors;
    }

    private PipePlan optimize(PipePlan plan, List<PhaseError> result) {
        // not yet
        return plan;
    }

    private PipePlan generatePlan(List<PhaseError> errors) {
        Set<Pipe> toGenerate = new HashSet<Pipe>(writes);
        Set<Pipe> generated = new HashSet<Pipe>();
        Set<Phase> planned = new HashSet<Phase>();
        Set<Pipe> obsolete = new HashSet<Pipe>();
        Set<Pipe> missing = new HashSet<Pipe>();
        // Map<DistFile,Collection<DistFile>> dependencies;
        PipePlan plan = new PipePlan();
        while (!toGenerate.isEmpty()) {
            Pipe file = toGenerate.iterator().next();
            toGenerate.remove(file); 
            if (build(file)) {
	            if (file.getProducer() == null) {
	                if (!generated.contains(file)) {
	                    log("File: " + file.getPath()
	                            + " exists and is up to date.\n");
	                    // ok already
	                    generated.add(file);
	                    plan.fileCreateWith(file, null);
	                }
	            } else {
	                Phase phase = file.getProducer();
	                plan.fileCreateWith(file, phase);
	                if (phase == null) {
	                    errors.add(new PhaseError("Don't know how to generate "
	                            + file.getPath()));
	                } else {
	                    if (file.exists())
	                        missing.add(file);
	                    else if (file.isObsolete())
	                        obsolete.add(file);
	                    List<Pipe> inputs = phase.getInputs();
	                    if (inputs != null) {
	                        for (Pipe input : inputs) {
	                            toGenerate.add(input);
	                            plan.processReads(phase, input);
	                        }
	                    }
	                    if (!planned.contains(phase)) {
	                        List<PhaseError> phaseErrors = phase.plan(this);
	                        if (!phaseErrors.isEmpty()) {
	                            errors.addAll(phaseErrors);
	                            return null;
	                        }
	                        planned.add(phase);
	                    }
	                }
	            }
            }  // if build
        }
        List<List<Phase>> waves = plan.plan();

        // partially ordered so we always print a producer before a consumer
        for (List<Phase> wave : waves) {
            for (Phase phase : wave) {
                log("Will run " + phase.getSummary()
                        + ", producing: ");
                for (Pipe output : phase.getOutputs()) {
                    log("  " + output.getPath());
                    if (missing.contains(output))
                        log(": missing");
                    else if (obsolete.contains(output))
                        log(": obsolete");
                    else
                        log("");
                }
            }
        }
        return plan;
    }

    /**
     * Determine if we should build this file or not
     * @param file The (target) file to consider
     * @return True if we should build.
     */
    private boolean build(Pipe<?> file) {
    	if (null == file.getConf()) {
    		file.setConf(getConf());
    	}

    	if (options.forceRebuild) {
    		return true;
    	}
    	
    	if (options.dryRun) {
    		return false;
    	}
    	
    	if (file.getProducer() == null) {
    		return false;
    	}
    	
        return file.isObsolete();
	}

	public String getName() {
        return name;
    }

    public JobConf getConf() {
        return baseConf;
    }

    void dryRun() throws InfeasiblePlanException {
        List<PhaseError> result = new ArrayList<PhaseError>();
        PipePlan plan = generatePlan(result);
        if (result.isEmpty()) {
            plan = optimize(plan, result);
        }
        if (!result.isEmpty()) {
            log("Plan errors:");
            for (PhaseError e : result) {
                log(e.getMessage());
            }
        }
    }

    /**
	 * Build and run the job
	 */
	@Override
	public int make() {
		
		// Setup implicit input based on command line parameters
		if (null == phases.get(0).getInputs()) {
			phases.get(0).reads(options.input);
		}

		// Setup implicit output based on command line parameters
		if (null == phases.get(phases.size() - 1).getOutputs()) {
			phases.get(phases.size() - 1).writes(options.output);
		}
		
		// Produces
		for(Phase phase: phases) {
			produces(phase.output());
		}
		
		// build plan for each phase
		List<List<PhaseError>> errorCollection = new ArrayList<List<PhaseError>>();
		for(Phase phase:phases) {
			errorCollection.add(phase.plan(this));
		}
		
		int errorCount = emitErrors(errorCollection);
		
		if (0 != errorCount) {
			return errorCount;
		}


		if (this.isDryRun()) {
            this.dryRun();
            return 0;
        }

		execute();
	
		return checkSuccess();
	}

	/**
	 * Return 0 if successful make(), 1 otherwise.
	 * @return Success value
	 */
	public int checkSuccess() {
		return (alerter.checkSuccess() ? 0:1);
	}

	/**
	 * @param errorCollection
	 * @return
	 */
	private int emitErrors(List<List<PhaseError>> errorCollection) {
		int errorCount = 0;
		if (null != errorCollection)
			for (List<PhaseError> errors : errorCollection) {
				if (null != errors)
					for (PhaseError e : errors) {
						logf("%s : %s \n", e.getMessage(), 
								(null == e.getException()) ? "" : e.getException().toString());
						errorCount++;
					}
			}
		return errorCount;
	}

	/**
	 * @return the dryRun
	 */
	private boolean isDryRun() {
		return options.dryRun;
	}
	
	private void log(String message) {
		System.out.print(message);
	}
	
	private void logf(String string, String message, String exceptionMessage) {
		System.out.printf(string, message, exceptionMessage);
	}

	/**
	 * Open file, assign to pipe, pipe will return an Iterator
	 */
	@Override
	public  Pipe subscribe(String URI) {
		TapfileRecordReader reader;
		Path path;
		try {
			path = new Path(URI);
			reader = new TapfileRecordReader(getConf(), new Path(URI));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
//		Pipe pipe = new Pipe(URI);  
		
		Pipe pipe = Pipe.of(reader.getMessageClass());
		pipe.setRecordReader(reader);
		System.out.println(path.getName());
		return pipe;
			
	}
	
	
	

}
