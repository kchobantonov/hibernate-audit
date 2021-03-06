/**
 * Copyright (C) 2009 Krasimir Chobantonov <kchobantonov@yahoo.com>
 * This file is part of Hibernate Audit.

 * Hibernate Audit is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 3 of the License, or (at your
 * option) any later version.
 * 
 * Hibernate Audit is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along
 * with Hibernate Audit.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.googlecode.hibernate.audit.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.Iterator;
import java.util.Properties;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.EMFCompare;
import org.eclipse.emf.compare.Match;
import org.eclipse.emf.compare.diff.DefaultDiffEngine;
import org.eclipse.emf.compare.diff.DiffBuilder;
import org.eclipse.emf.compare.diff.FeatureFilter;
import org.eclipse.emf.compare.diff.IDiffEngine;
import org.eclipse.emf.compare.diff.IDiffProcessor;
import org.eclipse.emf.compare.match.DefaultComparisonFactory;
import org.eclipse.emf.compare.match.DefaultEqualityHelperFactory;
import org.eclipse.emf.compare.match.IComparisonFactory;
import org.eclipse.emf.compare.match.IMatchEngine;
import org.eclipse.emf.compare.match.eobject.EditionDistance;
import org.eclipse.emf.compare.match.eobject.IEObjectMatcher;
import org.eclipse.emf.compare.match.eobject.internal.CachingDistance;
import org.eclipse.emf.compare.match.impl.MatchEngineFactoryImpl;
import org.eclipse.emf.compare.match.impl.MatchEngineFactoryRegistryImpl;
import org.eclipse.emf.compare.scope.IComparisonScope;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.URIConverter;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.emf.teneo.hibernate.HbDataStore;
import org.eclipse.emf.teneo.hibernate.HbDataStoreFactory;
import org.eclipse.emf.teneo.hibernate.HbHelper;
import org.eclipse.emf.teneo.hibernate.HbSessionDataStore;
import org.hibernate.engine.transaction.spi.SynchronizationRegistry;
import org.testng.Assert;

import com.googlecode.hibernate.audit.test.model1.Model1Package;

public abstract class AbstractHibernateAuditTest {
	protected final Logger LOG = Logger.getLogger(getClass());

	private static HbDataStoreFactory emfDataStoreFactory = new HbDataStoreFactory() {
		public HbDataStore createHbDataStore() {
			return new SessionFactory();
		}
	};

	private static final Logger HIBERNATE_TRANSACTION_LOG = Logger.getLogger(SynchronizationRegistry.class);

	protected static final HbDataStore dataStore = init();

	static {
		// this will ensure that we will get concurrency exceptions
		interceptLog(HIBERNATE_TRANSACTION_LOG);
	}

	private static void interceptLog(Logger logger) {
		if (Level.OFF.equals(logger.getLevel())) {
			logger.setLevel(Level.ERROR);
		}

		logger.addAppender(new AppenderSkeleton() {

			@Override
			public boolean requiresLayout() {
				return false;
			}

			@Override
			public void close() {
			}

			@Override
			protected void append(LoggingEvent event) {
				if (event.getThrowableInformation() != null
						&& event.getThrowableInformation().getThrowable() instanceof RuntimeException) {
					throw (RuntimeException) event.getThrowableInformation().getThrowable();
				}
			}
		});
	}

	protected String loadResource(String xmi) {
		InputStream in = this.getClass().getClassLoader().getResourceAsStream(xmi);
		if (in == null) {
			throw new IllegalArgumentException("Unable to locate resource " + xmi);
		}
		String result = null;
		try {
			result = readContentAsString(new BufferedReader(new InputStreamReader(in)));
		} catch (IOException ex) {
			throw new IllegalArgumentException("Unable to read resource " + xmi, ex);
		}

		return result;
	}

	private static String readContentAsString(Reader reader) throws IOException {
		StringBuffer result = new StringBuffer(1000);
		char[] buf = new char[1024];
		int numRead = 0;
		while ((numRead = reader.read(buf)) != -1) {
			result.append(buf, 0, numRead);
		}
		reader.close();
		return result.toString();
	}

	protected void assertEquals(String resourceURI, String loadedXmi, String storedXmi) {
		try {
			final ResourceSet resourceSet1 = new ResourceSetImpl();
			final ResourceSet resourceSet2 = new ResourceSetImpl();

			for (EPackage ePackage : dataStore.getEPackages()) {
				resourceSet1.getPackageRegistry().put(ePackage.getNsURI(), ePackage);
				resourceSet2.getPackageRegistry().put(ePackage.getNsURI(), ePackage);
			}

			Resource resource1 = load(URI.createURI("resource1"), resourceSet1, loadedXmi);
			Resource resource2 = load(URI.createURI("resource2"), resourceSet2, storedXmi);

			// Configure EMF Compare
			//IEObjectMatcher matcher = DefaultMatchEngine.createDefaultEObjectMatcher(UseIdentifiers.NEVER);
			final EditionDistance editionDistance = new EditionDistance();
			final CachingDistance cachedDistance = new CachingDistance(editionDistance);
			IEObjectMatcher matcher = new FixedProximityEObjectMatcher(cachedDistance);
			
			IComparisonFactory comparisonFactory = new DefaultComparisonFactory(new DefaultEqualityHelperFactory());
			IMatchEngine.Factory matchEngineFactory = new MatchEngineFactoryImpl(matcher, comparisonFactory);
			matchEngineFactory.setRanking(20);
			IMatchEngine.Factory.Registry matchEngineRegistry = new MatchEngineFactoryRegistryImpl();
			matchEngineRegistry.add(matchEngineFactory);
			
/*			IDiffProcessor diffProcessor = new DiffBuilder();
			IDiffEngine diffEngine = new DefaultDiffEngine(diffProcessor) {
				@Override
				protected FeatureFilter createFeatureFilter() {
					return new FeatureFilter() {
						@Override
						protected boolean isIgnoredAttribute(EAttribute attribute) {
							return attribute.isID() || super.isIgnoredAttribute(attribute);
						}
						
						@Override
						public boolean checkForOrderingChanges(EStructuralFeature feature) {
							return false;
						}
					};
				}
			};
*/			EMFCompare comparator = EMFCompare.builder()./*setDiffEngine(diffEngine).*/setMatchEngineFactoryRegistry(matchEngineRegistry).build();

			// Compare the two models
			IComparisonScope scope = EMFCompare.createDefaultScope(resource1.getContents().get(0), resource2.getContents().get(0));
			Comparison comparison = comparator.compare(scope);

			Assert.assertTrue(comparison.getDifferences().isEmpty(), "resourceURI=" + resourceURI + ",loadedXmi=\n"
					+ loadedXmi + "\nstoredXmi=\n" + storedXmi + "\n");
		} catch (IOException e) {
			LOG.error(e);
		}
	}

	private Resource load(URI uri, ResourceSet resourceSet, String data) throws IOException {
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
		Reader reader = new StringReader(data);
		URIConverter.ReadableInputStream input = new URIConverter.ReadableInputStream(reader, "UTF-8");
		Resource resource = resourceSet.createResource(uri);
		resource.load(input, null);
		
		return resource;
	}

	// init method
	private static HbDataStore init() {
		try {
			// Create the DataStore.
			final String dataStoreName = "AuditDataStore";
			HbHelper.setHbDataStoreFactory(emfDataStoreFactory);
			HbDataStore dataStore = HbHelper.INSTANCE.createRegisterDataStore(dataStoreName);

			// Configure the EPackages used by this DataStore.
			dataStore.setEPackages(new EPackage[] { Model1Package.eINSTANCE });

			// Initialize the DataStore. This sets up the Hibernate mapping and
			// creates the corresponding tables in the database.
			Properties prop = new Properties();
			prop.load(AbstractHibernateAuditTest.class.getResourceAsStream("/hibernate.properties"));
			dataStore.setHibernateProperties(prop);

			dataStore.initialize();

			return dataStore;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static class SessionFactory extends HbSessionDataStore {
	}
	
	
}
