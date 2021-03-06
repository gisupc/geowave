package mil.nga.giat.geowave.adapter.vector.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.text.ParseException;

import mil.nga.giat.geowave.adapter.vector.FeatureDataAdapter;
import mil.nga.giat.geowave.adapter.vector.stats.FeatureBoundingBoxStatistics;
import mil.nga.giat.geowave.adapter.vector.stats.FeatureNumericRangeStatistics;
import mil.nga.giat.geowave.adapter.vector.stats.FeatureTimeRangeStatistics;
import mil.nga.giat.geowave.adapter.vector.stats.StatsManager;
import mil.nga.giat.geowave.core.index.ByteArrayId;
import mil.nga.giat.geowave.core.store.adapter.statistics.DataStatistics;
import mil.nga.giat.geowave.core.store.data.visibility.GlobalVisibilityHandler;

import org.apache.accumulo.core.client.AccumuloException;
import org.apache.accumulo.core.client.AccumuloSecurityException;
import org.apache.commons.lang.ArrayUtils;
import org.geotools.data.DataUtilities;
import org.geotools.feature.SchemaException;
import org.geotools.filter.text.cql2.CQLException;
import org.junit.Before;
import org.junit.Test;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

public class StatsManagerTest
{

	private SimpleFeatureType schema;
	FeatureDataAdapter dataAdapter;

	@Before
	public void setup()
			throws AccumuloException,
			AccumuloSecurityException,
			SchemaException,
			CQLException,
			ParseException {
		schema = DataUtilities.createType(
				"sp.geostuff",
				"geometry:Geometry:srid=4326,pop:java.lang.Long,when:Date,whennot:Date,somewhere:Polygon,pid:String");
		dataAdapter = new FeatureDataAdapter(
				schema,
				new GlobalVisibilityHandler<SimpleFeature, Object>(
						"default"));
	}

	@Test
	public void test() {
		final StatsManager statsManager = new StatsManager(
				dataAdapter,
				schema);
		final ByteArrayId[] ids = statsManager.getSupportedStatisticsIds();
		assertEquals(
				6,
				ids.length);
		assertTrue(ArrayUtils.contains(
				ids,
				FeatureNumericRangeStatistics.composeId("pop")));
		assertTrue(ArrayUtils.contains(
				ids,
				FeatureBoundingBoxStatistics.composeId("somewhere")));
		assertTrue(ArrayUtils.contains(
				ids,
				FeatureBoundingBoxStatistics.composeId("geometry")));
		assertTrue(ArrayUtils.contains(
				ids,
				FeatureTimeRangeStatistics.composeId("when")));
		assertTrue(ArrayUtils.contains(
				ids,
				FeatureTimeRangeStatistics.composeId("whennot")));

		// can each type be created uniquely
		DataStatistics<SimpleFeature> stat = statsManager.createDataStatistics(
				dataAdapter,
				FeatureBoundingBoxStatistics.composeId("somewhere"));
		assertNotNull(stat);
		assertFalse(stat == statsManager.createDataStatistics(
				dataAdapter,
				FeatureBoundingBoxStatistics.composeId("somewhere")));

		FeatureBoundingBoxStatistics newStat = new FeatureBoundingBoxStatistics();
		newStat.fromBinary(stat.toBinary());
		assertEquals(
				newStat.getMaxY(),
				((FeatureBoundingBoxStatistics) stat).getMaxY(),
				0.001);
		assertEquals(
				newStat.getFieldName(),
				((FeatureBoundingBoxStatistics) stat).getFieldName());

		stat = statsManager.createDataStatistics(
				dataAdapter,
				FeatureTimeRangeStatistics.composeId("when"));
		assertNotNull(stat);
		assertFalse(stat == statsManager.createDataStatistics(
				dataAdapter,
				FeatureTimeRangeStatistics.composeId("when")));

		stat = statsManager.createDataStatistics(
				dataAdapter,
				FeatureNumericRangeStatistics.composeId("pop"));
		assertNotNull(stat);
		assertFalse(stat == statsManager.createDataStatistics(
				dataAdapter,
				FeatureNumericRangeStatistics.composeId("pop")));

	}
}
