package mil.nga.giat.geowave.core.ingest.kafka;

import java.util.HashMap;
import java.util.Map;

import org.apache.avro.specific.SpecificRecordBase;
//import org.apache.hadoop.fs.FSDataOutputStream;
//import org.apache.hadoop.fs.FileSystem;
//import org.apache.hadoop.fs.Path;
import org.apache.log4j.Logger;

public class StageKafkaData<T extends SpecificRecordBase>
{

	private final static Logger LOGGER = Logger.getLogger(StageKafkaData.class);
	private final Map<String, GenericAvroSerializer<T>> cachedWriters = new HashMap<String, GenericAvroSerializer<T>>();

	public StageKafkaData() {

	}

	public GenericAvroSerializer<T> getWriter(
			final String typeName,
			final StageToKafkaPlugin<?> plugin ) {
		return getDataWriterCreateIfNull(
				typeName,
				plugin);
	}

	private synchronized GenericAvroSerializer<T> getDataWriterCreateIfNull(
			final String typeName,
			final StageToKafkaPlugin<?> plugin ) {
		if (!cachedWriters.containsKey(typeName)) {
			GenericAvroSerializer<T> serializer = new GenericAvroSerializer<T>();
			cachedWriters.put(
					typeName,
					serializer);
		}
		return cachedWriters.get(typeName);
	}

	public synchronized void close() {}
}
