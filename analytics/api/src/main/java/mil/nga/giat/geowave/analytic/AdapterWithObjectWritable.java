package mil.nga.giat.geowave.analytic;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import mil.nga.giat.geowave.core.index.ByteArrayId;
import mil.nga.giat.geowave.datastore.accumulo.mapreduce.HadoopWritableSerializationTool;

import org.apache.hadoop.io.ObjectWritable;
import org.apache.hadoop.io.Writable;

public class AdapterWithObjectWritable implements
		Writable
{
	private ObjectWritable objectWritable;
	private ByteArrayId adapterId;
	private boolean isPrimary;

	public void setObject(
			final ObjectWritable data ) {
		objectWritable = data;
	}

	public ObjectWritable getObjectWritable() {
		return objectWritable;
	}

	protected void setObjectWritable(
			final ObjectWritable objectWritable ) {
		this.objectWritable = objectWritable;
	}

	public ByteArrayId getAdapterId() {
		return adapterId;
	}

	public void setAdapterId(
			final ByteArrayId adapterId ) {
		this.adapterId = adapterId;
	}

	public boolean isPrimary() {
		return isPrimary;
	}

	public void setPrimary(
			final boolean isPrimary ) {
		this.isPrimary = isPrimary;
	}

	@Override
	public void readFields(
			final DataInput input )
			throws IOException {
		final int adapterIdLength = input.readInt();
		final byte[] adapterIdBinary = new byte[adapterIdLength];
		input.readFully(adapterIdBinary);
		adapterId = new ByteArrayId(
				adapterIdBinary);
		isPrimary = input.readBoolean();
		if (objectWritable == null) {
			objectWritable = new ObjectWritable();
		}
		objectWritable.readFields(input);
	}

	@Override
	public void write(
			final DataOutput output )
			throws IOException {
		final byte[] adapterIdBinary = adapterId.getBytes();
		output.writeInt(adapterIdBinary.length);
		output.write(adapterIdBinary);
		output.writeBoolean(isPrimary);
		objectWritable.write(output);
	}

	public static void fillWritableWithAdapter(
			final HadoopWritableSerializationTool serializationTool,
			final AdapterWithObjectWritable writableToFill,
			final ByteArrayId adapterID,
			final boolean isPrimary,
			final Object entry ) {
		writableToFill.setAdapterId(adapterID);
		writableToFill.setPrimary(isPrimary);
		writableToFill.setObject(serializationTool.toWritable(
				adapterID,
				entry));
	}

	public static Object fromWritableWithAdapter(
			final HadoopWritableSerializationTool serializationTool,
			final AdapterWithObjectWritable writableToExtract ) {
		final ByteArrayId adapterID = writableToExtract.getAdapterId();
		final Object innerObj = writableToExtract.objectWritable.get();
		return (innerObj instanceof Writable) ? serializationTool.getHadoopWritableSerializerForAdapter(
				adapterID).fromWritable(
				(Writable) innerObj) : innerObj;
	}

}