package mil.nga.giat.geowave.core.ingest.kafka;

import java.io.File;

import mil.nga.giat.geowave.core.ingest.hdfs.HdfsPluginBase;
import mil.nga.giat.geowave.core.ingest.local.LocalPluginBase;

import org.apache.avro.specific.SpecificRecordBase;

public interface StageToKafkaPlugin<I> extends
		HdfsPluginBase,
		LocalPluginBase
{

	/**
	 * Read a file from the local file system and emit an array of intermediate
	 * data elements that will be serialized and staged to HDFS.
	 * 
	 * @param f
	 *            a local file that is supported by this plugin
	 * @return an array of intermediate data objects that will be serialized and
	 *         written to HDFS
	 */
	public <T extends SpecificRecordBase> T toAvroObject(
			File f );
}