package mil.nga.giat.geowave.format.landsat8;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterators;
import com.google.common.collect.MinMaxPriorityQueue;
import com.google.common.io.LineReader;
import com.vividsolutions.jts.geom.MultiPolygon;

public class SceneFeatureIterator implements
		SimpleFeatureIterator
{
	protected static class BestCloudCoverComparator implements
			Comparator<SimpleFeature>
	{

		@Override
		public int compare(
				final SimpleFeature first,
				final SimpleFeature second ) {
			return Float.compare(
					(Float) first.getAttribute(CLOUD_COVER_ATTRIBUTE_NAME),
					(Float) second.getAttribute(CLOUD_COVER_ATTRIBUTE_NAME));
		}
	}

	private final static Logger LOGGER = LoggerFactory.getLogger(SceneFeatureIterator.class);
	private static final String SCENES_GZ_URL = "http://landsat-pds.s3.amazonaws.com/scene_list.gz";
	protected static final String SCENES_TYPE_NAME = "scene";
	protected static final String SHAPE_ATTRIBUTE_NAME = "shape";
	protected static final String ACQUISITION_DATE_ATTRIBUTE_NAME = "acquisitionDate";
	protected static final String CLOUD_COVER_ATTRIBUTE_NAME = "cloudCover";
	protected static final String PROCESSING_LEVEL_ATTRIBUTE_NAME = "processingLevel";
	protected static final String PATH_ATTRIBUTE_NAME = "path";
	protected static final String ROW_ATTRIBUTE_NAME = "row";
	protected static final String SCENE_DOWNLOAD_ATTRIBUTE_NAME = "sceneDownloadUrl";
	protected static final String ENTITY_ID_ATTRIBUTE_NAME = "entityId";

	protected static final String[] SCENE_ATTRIBUTES = new String[] {
		SHAPE_ATTRIBUTE_NAME,
		ACQUISITION_DATE_ATTRIBUTE_NAME,
		CLOUD_COVER_ATTRIBUTE_NAME,
		PROCESSING_LEVEL_ATTRIBUTE_NAME,
		PATH_ATTRIBUTE_NAME,
		ROW_ATTRIBUTE_NAME,
		ENTITY_ID_ATTRIBUTE_NAME,
		SCENE_DOWNLOAD_ATTRIBUTE_NAME
	};
	protected static SimpleDateFormat AQUISITION_DATE_FORMAT = new SimpleDateFormat(
			"yyyy-MM-dd HH:mm:ss.SSS");
	private final String SCENES_DIR = "scenes";
	private final String COMPRESSED_FILE_NAME = "scene_list.gz";
	private final String CSV_FILE_NAME = "scene_list";
	private final String TEMP_CSV_FILE_NAME = "scene_list.tmp";
	private CSVParser parser;
	private Iterator<SimpleFeature> iterator;
	private SimpleFeatureType type;
	private CqlFilterPredicate filterPredicate;

	public SceneFeatureIterator(
			final boolean onlyScenesSinceLastRun,
			final boolean useCachedScenes,
			final int nBestScenes,
			final Filter cqlFilter,
			final String workspaceDir )
			throws MalformedURLException,
			IOException {
		init(
				new File(
						workspaceDir,
						SCENES_DIR),
				onlyScenesSinceLastRun,
				useCachedScenes,
				nBestScenes,
				new WRS2GeometryStore(
						workspaceDir),
				cqlFilter);
	}

	public void setFilterEnabled(
			final boolean enabled ) {
		if (filterPredicate != null) {
			filterPredicate.setEnabled(enabled);
		}
	}

	private void init(
			final File scenesDir,
			final boolean onlyScenesSinceLastRun,
			final boolean useCachedScenes,
			final int nBestScenes,
			final WRS2GeometryStore geometryStore,
			final Filter cqlFilter )
			throws IOException {
		scenesDir.mkdirs();
		final File csvFile = new File(
				scenesDir,
				CSV_FILE_NAME);
		long startLine = 0;
		if (!csvFile.exists() || !useCachedScenes) {
			final File compressedFile = new File(
					scenesDir,
					COMPRESSED_FILE_NAME);
			final File tempCsvFile = new File(
					scenesDir,
					TEMP_CSV_FILE_NAME);
			if (compressedFile.exists()) {
				compressedFile.delete();
			}
			if (tempCsvFile.exists()) {
				tempCsvFile.delete();
			}
			InputStream in = null;
			// first download the gzipped file
			try {
				in = new URL(
						SCENES_GZ_URL).openStream();
				final FileOutputStream outStream = new FileOutputStream(
						compressedFile);
				IOUtils.copyLarge(
						in,
						outStream);
				outStream.close();
			}
			catch (final IOException e) {
				LOGGER.warn(
						"Unable to read scenes from public S3",
						e);
				throw e;
			}
			finally {
				if (in != null) {
					IOUtils.closeQuietly(in);
				}
			}
			// next unzip to CSV
			GzipCompressorInputStream gzIn = null;
			FileOutputStream out = null;
			try {
				final FileInputStream fin = new FileInputStream(
						compressedFile);
				final BufferedInputStream bin = new BufferedInputStream(
						fin);
				out = new FileOutputStream(
						tempCsvFile);
				gzIn = new GzipCompressorInputStream(
						bin);
				final byte[] buffer = new byte[1024];
				int n = 0;
				while (-1 != (n = gzIn.read(buffer))) {
					out.write(
							buffer,
							0,
							n);
				}
				// once we have a csv we can cleanup the compressed file
				compressedFile.delete();
				out.close();
			}
			catch (final IOException e) {
				LOGGER.warn(
						"Unable to extract scenes file",
						e);
				throw e;
			}
			finally {
				if (out != null) {
					IOUtils.closeQuietly(out);
				}
				if (gzIn != null) {
					IOUtils.closeQuietly(gzIn);
				}
			}
			if (onlyScenesSinceLastRun && csvFile.exists()) {
				// seek the number of lines of the existing file
				final LineReader lines = new LineReader(
						new FileReader(
								csvFile));
				while (lines.readLine() != null) {
					startLine++;
				}
			}
			if (csvFile.exists()) {
				csvFile.delete();
			}
			tempCsvFile.renameTo(csvFile);
		}
		// initialize the feature type
		final SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
		typeBuilder.setName(SCENES_TYPE_NAME);
		typeBuilder.add(
				SHAPE_ATTRIBUTE_NAME,
				MultiPolygon.class);
		typeBuilder.add(
				ENTITY_ID_ATTRIBUTE_NAME,
				String.class);
		typeBuilder.add(
				ACQUISITION_DATE_ATTRIBUTE_NAME,
				Date.class);
		typeBuilder.add(
				CLOUD_COVER_ATTRIBUTE_NAME,
				Float.class);
		typeBuilder.add(
				PROCESSING_LEVEL_ATTRIBUTE_NAME,
				String.class);
		typeBuilder.add(
				PATH_ATTRIBUTE_NAME,
				Integer.class);
		typeBuilder.add(
				ROW_ATTRIBUTE_NAME,
				Integer.class);
		typeBuilder.add(
				SCENE_DOWNLOAD_ATTRIBUTE_NAME,
				String.class);
		type = typeBuilder.buildFeatureType();
		setupCsvToFeatureIterator(
				csvFile,
				startLine,
				geometryStore,
				cqlFilter);
		if (nBestScenes > 0) {
			final String[] attributes = DataUtilities.attributeNames(
					cqlFilter,
					type);
			// rely on best scene aggregation at a higher level if the filter is
			// using attributes not contained in the scene

			boolean skipBestScenes = false;
			for (final String attr : attributes) {
				if (!ArrayUtils.contains(
						SCENE_ATTRIBUTES,
						attr)) {
					skipBestScenes = true;
					break;
				}
			}
			if (!skipBestScenes) {
				nBestScenes(nBestScenes);
			}
		}
	}

	private void nBestScenes(
			final int n ) {
		iterator = nBestScenes(
				this,
				n);
	}

	protected static Iterator<SimpleFeature> nBestScenes(
			final SimpleFeatureIterator iterator,
			final int n ) {
		final MinMaxPriorityQueue<SimpleFeature> bestScenes = MinMaxPriorityQueue.orderedBy(
				new BestCloudCoverComparator()).maximumSize(
				n).create();
		// iterate once through the scenes, saving the best entity IDs
		// based on cloud cover

		while (iterator.hasNext()) {
			bestScenes.offer(iterator.next());
		}
		iterator.close();
		return bestScenes.iterator();
	}

	private void setupCsvToFeatureIterator(
			final File csvFile,
			final long startLine,
			final WRS2GeometryStore geometryStore,
			final Filter cqlFilter )
			throws FileNotFoundException,
			IOException {
		parser = new CSVParser(
				new FileReader(
						csvFile),
				CSVFormat.DEFAULT.withHeader().withSkipHeaderRecord());
		final Iterator<CSVRecord> csvIterator = parser.iterator();
		long startLineDecrementor = startLine;
		// we skip the header, so only skip to start line 1
		while ((startLineDecrementor > 1) && csvIterator.hasNext()) {
			startLineDecrementor--;
			csvIterator.next();
		}

		// wrap the iterator with a feature conversion and a filter (if
		// provided)
		iterator = Iterators.transform(
				csvIterator,
				new CSVToFeatureTransform(
						geometryStore,
						type));
		if (cqlFilter != null) {
			filterPredicate = new CqlFilterPredicate(
					cqlFilter);
			iterator = Iterators.filter(
					iterator,
					filterPredicate);
		}
	}

	public SimpleFeatureType getFeatureType() {
		return type;
	}

	@Override
	public void close() {
		if (parser != null) {
			try {
				parser.close();
				parser = null;
			}
			catch (final IOException e) {
				LOGGER.warn(
						"Unable to close CSV parser",
						parser);
			}
		}
	}

	@Override
	public boolean hasNext() {
		if (iterator != null) {
			return iterator.hasNext();
		}
		return false;
	}

	@Override
	public SimpleFeature next()
			throws NoSuchElementException {
		if (iterator != null) {
			return iterator.next();
		}
		return null;
	}

	private static class CSVToFeatureTransform implements
			Function<CSVRecord, SimpleFeature>
	{
		// shape (Geometry), entityId (String), acquisitionDate (Date),
		// cloudCover (double), processingLevel (String), path (int), row (int)
		private final WRS2GeometryStore wrs2Geometry;
		private final SimpleFeatureBuilder featureBuilder;

		public CSVToFeatureTransform(
				final WRS2GeometryStore wrs2Geometry,
				final SimpleFeatureType type ) {
			this.wrs2Geometry = wrs2Geometry;

			featureBuilder = new SimpleFeatureBuilder(
					type);
		}

		// entityId,acquisitionDate,cloudCover,processingLevel,path,row,min_lat,min_lon,max_lat,max_lon,download_url
		@Override
		public SimpleFeature apply(
				final CSVRecord input ) {
			final String entityId = input.get("entityId");
			final double cloudCover = Double.parseDouble(input.get("cloudCover"));
			final String processingLevel = input.get("processingLevel");
			final int path = Integer.parseInt(input.get("path"));
			final int row = Integer.parseInt(input.get("row"));
			final String downloadUrl = input.get("download_url");

			final MultiPolygon shape = wrs2Geometry.getGeometry(
					path,
					row);
			featureBuilder.add(shape);
			featureBuilder.add(entityId);
			Date aquisitionDate;
			try {
				aquisitionDate = AQUISITION_DATE_FORMAT.parse(input.get("acquisitionDate"));
				featureBuilder.add(aquisitionDate);
			}
			catch (final ParseException e) {
				LOGGER.warn(
						"Unable to parse aquisition date",
						e);

				featureBuilder.add(null);
			}

			featureBuilder.add(cloudCover);
			featureBuilder.add(processingLevel);
			featureBuilder.add(path);
			featureBuilder.add(row);
			featureBuilder.add(downloadUrl);
			return featureBuilder.buildFeature(entityId);
		}
	}

	private static class CqlFilterPredicate implements
			Predicate<SimpleFeature>
	{
		private final Filter cqlFilter;
		private boolean enabled = true;

		public CqlFilterPredicate(
				final Filter cqlFilter ) {
			this.cqlFilter = cqlFilter;
		}

		@Override
		public boolean apply(
				final SimpleFeature input ) {
			if (!enabled) {
				return true;
			}
			return cqlFilter.evaluate(input);
		}

		public void setEnabled(
				final boolean enabled ) {
			this.enabled = enabled;
		}

	}
}
