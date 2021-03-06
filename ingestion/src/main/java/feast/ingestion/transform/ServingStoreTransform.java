/*
 * Copyright 2018 The Feast Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package feast.ingestion.transform;

import com.google.inject.Inject;
import feast.ingestion.metrics.FeastMetrics;
import feast.ingestion.model.Specs;
import feast.ingestion.values.PFeatureRows;
import feast.store.serving.FeatureServingFactory;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;

@Slf4j
public class ServingStoreTransform extends PTransform<PFeatureRows, PFeatureRows> {

  private List<FeatureServingFactory> stores;
  private Specs specs;

  @Inject
  public ServingStoreTransform(List<FeatureServingFactory> stores, Specs specs) {
    this.stores = stores;
    this.specs = specs;
  }

  @Override
  public PFeatureRows expand(PFeatureRows input) {
    PFeatureRows output =
        input.apply(
            "Split to serving stores",
            new SplitOutputByStore(
                stores, (featureSpec) -> featureSpec.getDataStores().getServing().getId(), specs,
                specs.getServingStorageSpecs()));

    output.getMain().apply("metrics.store.lag", ParDo.of(FeastMetrics.lagUpdateDoFn()));
    output.getMain().apply("metrics.store.main", ParDo.of(FeastMetrics.incrDoFn("serving_stored")));
    output.getErrors()
        .apply("metrics.store.errors", ParDo.of(FeastMetrics.incrDoFn("serving_errors")));
    return output;
  }
}
