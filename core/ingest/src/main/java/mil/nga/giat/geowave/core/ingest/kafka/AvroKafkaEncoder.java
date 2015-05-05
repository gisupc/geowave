package mil.nga.giat.geowave.core.ingest.kafka;

import kafka.serializer.Encoder;
import kafka.utils.VerifiableProperties;

import org.apache.avro.specific.SpecificRecordBase;

public class AvroKafkaEncoder<T extends SpecificRecordBase> implements
		Encoder<T>
{
	private final GenericAvroSerializer<T> serializer = new GenericAvroSerializer<T>();

	public AvroKafkaEncoder(
			final VerifiableProperties verifiableProperties ) {
		// This constructor must be present to avoid runtime errors
	}

	@Override
	public byte[] toBytes(
			final T object ) {
		return serializer.serialize(
				object,
				object.getSchema());
	}
}