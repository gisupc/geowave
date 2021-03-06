package mil.nga.giat.geowave.format.geolife;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import mil.nga.giat.geowave.adapter.vector.FeatureDataAdapter;
import mil.nga.giat.geowave.adapter.vector.ingest.AbstractSimpleFeatureIngestPlugin;
import mil.nga.giat.geowave.core.geotime.GeometryUtils;
import mil.nga.giat.geowave.core.geotime.IndexType;
import mil.nga.giat.geowave.core.index.ByteArrayId;
import mil.nga.giat.geowave.core.index.StringUtils;
import mil.nga.giat.geowave.core.ingest.GeoWaveData;
import mil.nga.giat.geowave.core.ingest.hdfs.HdfsFile;
import mil.nga.giat.geowave.core.ingest.hdfs.mapreduce.IngestWithMapper;
import mil.nga.giat.geowave.core.ingest.hdfs.mapreduce.IngestWithReducer;
import mil.nga.giat.geowave.core.store.CloseableIterator;
import mil.nga.giat.geowave.core.store.adapter.WritableDataAdapter;
import mil.nga.giat.geowave.core.store.data.field.FieldVisibilityHandler;
import mil.nga.giat.geowave.core.store.data.visibility.GlobalVisibilityHandler;
import mil.nga.giat.geowave.core.store.index.Index;

import org.apache.avro.Schema;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.vividsolutions.jts.geom.Coordinate;

/*
 */
public class GeoLifeIngestPlugin extends
		AbstractSimpleFeatureIngestPlugin<HdfsFile>
{

	private final static Logger LOGGER = Logger.getLogger(GeoLifeIngestPlugin.class);

	private final SimpleFeatureBuilder geolifePointBuilder;
	private final SimpleFeatureType geolifePointType;

	private final SimpleFeatureBuilder geolifeTrackBuilder;
	private final SimpleFeatureType geolifeTrackType;

	private final ByteArrayId pointKey;
	private final ByteArrayId trackKey;

	private final Index[] supportedIndices;

	public GeoLifeIngestPlugin() {
		geolifePointType = GeoLifeUtils.createGeoLifePointDataType();
		pointKey = new ByteArrayId(
				StringUtils.stringToBinary(GeoLifeUtils.GEOLIFE_POINT_FEATURE));
		geolifePointBuilder = new SimpleFeatureBuilder(
				geolifePointType);

		geolifeTrackType = GeoLifeUtils.createGeoLifeTrackDataType();
		trackKey = new ByteArrayId(
				StringUtils.stringToBinary(GeoLifeUtils.GEOLIFE_TRACK_FEATURE));
		geolifeTrackBuilder = new SimpleFeatureBuilder(
				geolifeTrackType);

		supportedIndices = new Index[] {
			IndexType.SPATIAL_VECTOR.createDefaultIndex(),
			IndexType.SPATIAL_TEMPORAL_VECTOR.createDefaultIndex()
		};

	}

	@Override
	public String[] getFileExtensionFilters() {
		return new String[] {
			"plt"
		};
	}

	@Override
	public void init(
			final File baseDirectory ) {

	}

	@Override
	public boolean supportsFile(
			final File file ) {
		return GeoLifeUtils.validate(file);
	}

	@Override
	public Index[] getSupportedIndices() {
		return supportedIndices;
	}

	@Override
	public WritableDataAdapter<SimpleFeature>[] getDataAdapters(
			final String globalVisibility ) {
		final FieldVisibilityHandler<SimpleFeature, Object> fieldVisiblityHandler = ((globalVisibility != null) && !globalVisibility.isEmpty()) ? new GlobalVisibilityHandler<SimpleFeature, Object>(
				globalVisibility) : null;
		return new WritableDataAdapter[] {
			new FeatureDataAdapter(
					geolifePointType,
					fieldVisiblityHandler),
			new FeatureDataAdapter(
					geolifeTrackType,
					fieldVisiblityHandler)

		};
	}

	@Override
	public Schema getAvroSchemaForHdfsType() {
		return HdfsFile.getClassSchema();
	}

	@Override
	public HdfsFile[] toHdfsObjects(
			final File input ) {
		final HdfsFile hfile = new HdfsFile();
		hfile.setOriginalFilePath(input.getAbsolutePath());
		try {
			hfile.setOriginalFile(ByteBuffer.wrap(Files.readAllBytes(input.toPath())));
		}
		catch (final IOException e) {
			LOGGER.warn(
					"Unable to read GeoLife file: " + input.getAbsolutePath(),
					e);
			return new HdfsFile[] {};
		}

		return new HdfsFile[] {
			hfile
		};
	}

	@Override
	public boolean isUseReducerPreferred() {
		return false;
	}

	@Override
	public IngestWithMapper<HdfsFile, SimpleFeature> ingestWithMapper() {
		return new IngestGeoLifeFromHdfs(
				this);
	}

	@Override
	public IngestWithReducer<HdfsFile, ?, ?, SimpleFeature> ingestWithReducer() {
		// unsupported right now
		throw new UnsupportedOperationException(
				"GeoLife tracks cannot be ingested with a reducer");
	}

	@Override
	protected CloseableIterator<GeoWaveData<SimpleFeature>> toGeoWaveDataInternal(
			final HdfsFile hfile,
			final ByteArrayId primaryIndexId,
			final String globalVisibility ) {

		final List<GeoWaveData<SimpleFeature>> featureData = new ArrayList<GeoWaveData<SimpleFeature>>();

		final InputStream in = new ByteArrayInputStream(
				hfile.getOriginalFile().array());
		final InputStreamReader isr = new InputStreamReader(
				in,
				StringUtils.UTF8_CHAR_SET);
		final BufferedReader br = new BufferedReader(
				isr);
		int pointInstance = 0;
		final List<Coordinate> pts = new ArrayList<Coordinate>();
		final String trackId = FilenameUtils.getName(hfile.getOriginalFilePath().toString());
		String line;
		Date startTimeStamp = null;
		Date endTimeStamp = null;
		String timestring = "";
		try {
			while ((line = br.readLine()) != null) {

				final String[] vals = line.split(",");
				if (vals.length != 7) {
					continue;
				}

				final Coordinate cord = new Coordinate(
						Double.parseDouble(vals[1]),
						Double.parseDouble(vals[0]));
				pts.add(cord);
				geolifePointBuilder.set(
						"geometry",
						GeometryUtils.GEOMETRY_FACTORY.createPoint(cord));
				geolifePointBuilder.set(
						"trackid",
						trackId);
				geolifePointBuilder.set(
						"pointinstance",
						pointInstance);
				pointInstance++;

				timestring = vals[5] + " " + vals[6];
				final Date ts = GeoLifeUtils.parseDate(timestring);
				geolifePointBuilder.set(
						"Timestamp",
						ts);
				if (startTimeStamp == null) {
					startTimeStamp = ts;
				}
				endTimeStamp = ts;

				geolifePointBuilder.set(
						"Latitude",
						Double.parseDouble(vals[0]));
				geolifePointBuilder.set(
						"Longitude",
						Double.parseDouble(vals[1]));

				Double elevation = Double.parseDouble(vals[3]);
				if (elevation == -777) {
					elevation = null;
				}
				geolifePointBuilder.set(
						"Elevation",
						elevation);
				featureData.add(new GeoWaveData<SimpleFeature>(
						pointKey,
						primaryIndexId,
						geolifePointBuilder.buildFeature(trackId + "_" + pointInstance)));
			}

			geolifeTrackBuilder.set(
					"geometry",
					GeometryUtils.GEOMETRY_FACTORY.createLineString(pts.toArray(new Coordinate[pts.size()])));

			geolifeTrackBuilder.set(
					"StartTimeStamp",
					startTimeStamp);
			geolifeTrackBuilder.set(
					"EndTimeStamp",
					endTimeStamp);
			if ((endTimeStamp != null) && (startTimeStamp != null)) {
				geolifeTrackBuilder.set(
						"Duration",
						endTimeStamp.getTime() - startTimeStamp.getTime());
			}
			geolifeTrackBuilder.set(
					"NumberPoints",
					pointInstance);
			geolifeTrackBuilder.set(
					"TrackId",
					trackId);
			featureData.add(new GeoWaveData<SimpleFeature>(
					trackKey,
					primaryIndexId,
					geolifeTrackBuilder.buildFeature(trackId)));

		}
		catch (final IOException e) {
			LOGGER.warn(
					"Error reading line from file: " + hfile.getOriginalFilePath(),
					e);
		}
		catch (final ParseException e) {
			LOGGER.error(
					"Error parsing time string: " + timestring,
					e);
		}
		finally {
			IOUtils.closeQuietly(br);
			IOUtils.closeQuietly(isr);
			IOUtils.closeQuietly(in);
		}

		return new CloseableIterator.Wrapper<GeoWaveData<SimpleFeature>>(
				featureData.iterator());
	}

	@Override
	public Index[] getRequiredIndices() {
		return new Index[] {};
	}

	public static class IngestGeoLifeFromHdfs extends
			AbstractIngestSimpleFeatureWithMapper<HdfsFile>
	{
		public IngestGeoLifeFromHdfs() {
			this(
					new GeoLifeIngestPlugin());
		}

		public IngestGeoLifeFromHdfs(
				final GeoLifeIngestPlugin parentPlugin ) {
			super(
					parentPlugin);
		}
	}
}
