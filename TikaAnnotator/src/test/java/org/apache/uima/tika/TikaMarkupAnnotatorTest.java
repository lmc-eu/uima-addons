/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.uima.tika;

import com.google.common.collect.Maps;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.TypeSystemDescriptionFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Optional;

/**
 * TestCase for {@link MarkupAnnotator}  - real one, on several files.
 */
public class TikaMarkupAnnotatorTest {

    protected final TestData TESTING_DOCS = TestData.DEFAULT;

    private final String originalView = "_InitialView";

    @Test
    public void realParseTest() throws UIMAException, IOException {

        TypeSystemDescription typeSystem = TypeSystemDescriptionFactory.createTypeSystemDescriptionFromPath(
                getClass().getResource("/MarkupAnnotationTypeSystem.xml").toString()
        );

        AnalysisEngine ae = AnalysisEngineFactory.createEngine(MarkupAnnotator.class, typeSystem);

        Map<JCas, String> results = Maps.newIdentityHashMap();

        for (String filename : TESTING_DOCS.getFilenames()) {
            System.err.println("loading: " + filename);
            JCas cas = ae.newJCas();
/*
            try (final InputStream is = getClass().getResourceAsStream("/docs/" + filename)) {
                final byte[] bytes = IOUtils.toByteArray(is);
                FeatureStructure array = FSCollectionFactory.createByteArray(cas, bytes);
                cas.setSofaDataArray(array, "application/octet-stream");
            }
*/
            final URL url = getClass().getResource("/docs/" + filename);
            cas.setSofaDataURI(url.toString(), "application/octet-stream");

            System.err.println("parsing: " + filename);
            SimplePipeline.runPipeline(cas, ae);

            //TODO: this should be done by annotator; fix that!
            results.put(cas.getView("textView"), filename);
        }

        TESTING_DOCS.checkResults(results.keySet(), Optional.of(results::get));
    }

}
