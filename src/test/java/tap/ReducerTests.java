package tap;

import static org.junit.Assert.*;
import junit.framework.Assert;

import org.junit.Test;

import tap.CommandOptions;
import tap.Phase;
import tap.Pipe;
import tap.Tap;
import tap.core.SummationPipeMapper;
import tap.core.SummationPipeReducer;

public class ReducerTests {

    @Test
    public void summation() {
    	
        /*
         * Parse options - just use the standard options - input and output
         * location, time window, etc.
         */

        String args[] = { "-o", "/tmp/wordcount", "-i", "/tmp/out", "-f" };
        Assert.assertEquals(5, args.length);
        CommandOptions o = new CommandOptions(args);
        /* Set up a basic pipeline of map reduce */
        Tap summation = new Tap(o).named("summation");
        
        Assert.assertNotNull("must specify input directory", o.input);
        Assert.assertNotNull("must specify output directory", o.output);

        Pipe<CountRec> input = Pipe.of(CountRec.class).at(o.input);
        input.setPrototype(new CountRec());

        Pipe<OutputLog> output = Pipe.of(OutputLog.class).at(o.output);
        output.setPrototype(new OutputLog());

        summation.produces(output);

        Phase sum = new Phase().reads(input).writes(output)
                .map(SummationPipeMapper.class).groupBy("word")
                .reduce(SummationPipeReducer.class);
        
        sum.plan(summation);
        
        if (o.forceRebuild)
            summation.forceRebuild();

        summation.dryRun();

        Assert.assertNotNull("Mapper Out Pipe Class ", sum.getConf().get(Phase.MAP_OUT_PIPE_CLASS));
        Assert.assertNotNull("Reducer Out Pipe Class should be specified ", sum.getConf().get(Phase.REDUCER_OUT_PIPE_CLASS));
        Assert.assertNotNull("Reducer should be specified ", sum.getConf().get(Phase.REDUCER));


        summation.execute();
    }

}
