package tap.core.io.avro;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import org.apache.avro.Schema;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;

import tap.core.io.BinaryKey;
import tap.core.io.Bytes;
import tap.core.io.SortOrder;

public class BinaryKeyDatumReader implements DatumReader<BinaryKey> {
	
	private byte[] bytes = new byte[Bytes.SIZEOF_INT];
	
	@Override
	public void setSchema(Schema schema) {
	}

	@Override
	public BinaryKey read(BinaryKey reuse, Decoder in) throws IOException {
		in.readFixed(bytes);
		int length = Bytes.toInt(bytes, 0, SortOrder.ASCENDING);
		
		in.readFixed(bytes);
		int groupLength = Bytes.toInt(bytes, 0, SortOrder.ASCENDING);
		
		byte[] data = new byte[length + BinaryKey.KEY_BYTES_OFFSET];
		
		// write back length & group length to data buffer
		Bytes.putInt(data, 0, length, SortOrder.ASCENDING);
		Bytes.putInt(data, Bytes.SIZEOF_INT, groupLength, SortOrder.ASCENDING);
		
		// read key bytes
		in.readFixed(data, BinaryKey.KEY_BYTES_OFFSET, length);

		BinaryKey result = reuse != null ? reuse : new BinaryKey();
		result.set(data, data.length);
		return result; 
	}
}
