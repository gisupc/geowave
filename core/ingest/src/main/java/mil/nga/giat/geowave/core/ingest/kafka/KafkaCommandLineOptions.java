package mil.nga.giat.geowave.core.ingest.kafka;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;

public class KafkaCommandLineOptions
{

	private final static Logger LOGGER = Logger.getLogger(KafkaCommandLineOptions.class);
	private final String kafkaTopic;
	private final String kafkaPropertiesPath;
	protected static Properties properties;

	public KafkaCommandLineOptions(
			final String kafkaTopic,
			final String kafkaPropertiesPath ) {
		this.kafkaTopic = kafkaTopic;
		this.kafkaPropertiesPath = kafkaPropertiesPath;
	}

	public static void applyOptions(
			final Options allOptions ) {
		allOptions.addOption(
				"kafkatopic",
				true,
				"Kafka topic name where data will be emitted to");
		allOptions.addOption(
				"kafkaprops",
				true,
				"Properties file containing Kafka properties");
	}

	public String getKafkaTopic() {
		return kafkaTopic;
	}

	public String getKafkaPropertiesPath() {
		return kafkaPropertiesPath;
	}

	public static Properties getProperties() {
		return properties;
	}

	public static KafkaCommandLineOptions parseOptions(
			final CommandLine commandLine )
			throws ParseException {
		final String kafkaTopic = commandLine.getOptionValue("kafkatopic");
		final String kafkaPropertiesPath = commandLine.getOptionValue("kafkaprops");
		boolean success = true;
		if (kafkaTopic == null) {
			success = false;
			LOGGER.fatal("Kafka topic not provided");
		}
		if (kafkaPropertiesPath == null) {
			success = false;
			LOGGER.fatal("Kafka properties file not provided");
		}
		if (!success) {
			throw new ParseException(
					"Required option is missing");
		}
		
		properties = new Properties();
		try {
			properties.load(new FileReader(
					new File(
							kafkaPropertiesPath)));
		}
		catch (FileNotFoundException e) {
			success = false;
			LOGGER.fatal("Kafka properties file not found: " + e.getMessage());
		}
		catch (IOException e) {
			success = false;
			LOGGER.fatal("Unable to load Kafka properties file: " + e.getMessage());
		}

		if (!success) {
			throw new ParseException(
					"Required option is missing");
		}

		return new KafkaCommandLineOptions(
				kafkaTopic,
				kafkaPropertiesPath);
	}
}
