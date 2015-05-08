package mil.nga.giat.geowave.test.kafka;

import java.io.IOException;
import java.util.Properties;

import kafka.admin.AdminUtils;
import kafka.server.KafkaConfig;
import kafka.server.KafkaServerStartable;
import kafka.utils.MockTime;
import kafka.utils.Time;
import kafka.utils.ZKStringSerializer$;
import kafka.zk.EmbeddedZookeeper;
import mil.nga.giat.geowave.core.cli.GeoWaveMain;
import mil.nga.giat.geowave.test.GeoWaveTestEnvironment;

import org.I0Itec.zkclient.ZkClient;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.junit.BeforeClass;

abstract public class KafkaTestEnvironment extends
		GeoWaveTestEnvironment

{
	private final static Logger LOGGER = Logger.getLogger(KafkaTestEnvironment.class);

	protected static String KAFKA_TEST_TOPIC = "gpxtesttopic";
	protected static EmbeddedZookeeper zkServer;
	protected static KafkaServerStartable kafkaServer;

	protected void testKafkaStage(
			final String ingestFilePath ) {
		LOGGER.warn("Ingesting '" + ingestFilePath + "' - this may take several minutes...");
		String[] args = null;
		synchronized (MUTEX) {
			args = StringUtils.split(
					"-kafkastage -kafkatopic" + KAFKA_TEST_TOPIC + " -f gpx -b " + ingestFilePath,
					' ');
		}

		// -kafkastage -kafkatopic gpxtopic -kafkaprops /tmp/config.properties
		// -b C:\data\gpx\gpx-planet-2013-04-09

		GeoWaveMain.main(args);
	}

	@BeforeClass
	public static void setupKafkaServer()
			throws IOException {

//		GeoWaveTestEnvironment.setup();
//		String zookeeper2 = System.getProperty("zookeeperUrl");
//		zkServer = new EmbeddedZookeeper(
//				zookeeper);
//		ZkClient zkClient = new ZkClient(
//				zkServer.connectString(),
//				30000,
//				30000,
//				ZKStringSerializer$.MODULE$);
//
//		// Time mock = new MockTime();
//		kafkaServer = new KafkaServerStartable(
//				getKafkaConfig(zookeeper));
		//
		// TestUtils.create(
		// getKafkaConfig(zookeeper),
		// mock);

//		kafkaServer.startup();

		// create the topic
//		AdminUtils.createTopic(
//				zkClient,
//				KAFKA_TEST_TOPIC,
//				1,
//				1,
//				new Properties());
	}

	private static KafkaConfig getKafkaConfig(
			final String zkConnectString ) {

		Properties config = new Properties();
		config.put(
				"metadata.broker.list",
				"localhost:9092");
		config.put(
				"zookeeper.hosts",
				"localhost:2181");
		config.put(
				"serializer.class",
				"mil.nga.giat.geowave.core.ingest.kafka.AvroKafkaEncoder");
		KafkaConfig kafkaConfig = new KafkaConfig(
				config);

		return kafkaConfig;
	}

}
