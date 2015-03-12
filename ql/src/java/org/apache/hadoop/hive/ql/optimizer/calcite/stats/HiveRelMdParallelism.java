/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hive.ql.optimizer.calcite.stats;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.metadata.ReflectiveRelMetadataProvider;
import org.apache.calcite.rel.metadata.RelMdParallelism;
import org.apache.calcite.rel.metadata.RelMetadataProvider;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.util.BuiltInMethod;
import org.apache.hadoop.hive.ql.optimizer.calcite.reloperators.HiveJoin;
import org.apache.hadoop.hive.ql.optimizer.calcite.reloperators.HiveJoin.JoinAlgorithm;
import org.apache.hadoop.hive.ql.optimizer.calcite.reloperators.HiveSort;

public class HiveRelMdParallelism extends RelMdParallelism {

  private final Double maxSplitSize;

  //~ Constructors -----------------------------------------------------------

  public HiveRelMdParallelism(Double maxSplitSize) {
    this.maxSplitSize = maxSplitSize;
  }

  public RelMetadataProvider getMetadataProvider() {
    return ReflectiveRelMetadataProvider.reflectiveSource(this,
                BuiltInMethod.IS_PHASE_TRANSITION.method,
                BuiltInMethod.SPLIT_COUNT.method);
  }

  //~ Methods ----------------------------------------------------------------

  public Boolean isPhaseTransition(HiveJoin join) {
    // As Exchange operator is introduced later on, we make a
    // common join operator create a new stage for the moment
    if (join.getJoinAlgorithm() == JoinAlgorithm.COMMON_JOIN) {
      return true;
    }
    return false;
  }

  public Boolean isPhaseTransition(HiveSort sort) {
    // As Exchange operator is introduced later on, we make a
    // sort operator create a new stage for the moment
    return true;
  }

  public Integer splitCount(RelNode rel) {
    Boolean newPhase = RelMetadataQuery.isPhaseTransition(rel);

    if (newPhase == null) {
      return null;
    }

    if (newPhase) {
      // We repartition: new number of splits
      final Double averageRowSize = RelMetadataQuery.getAverageRowSize(rel);
      final Double rowCount = RelMetadataQuery.getRowCount(rel);
      if (averageRowSize == null || rowCount == null) {
        return null;
      }
      final Double totalSize = averageRowSize * rowCount;
      final Double splitCount = totalSize / maxSplitSize;
      return splitCount.intValue();
    }

    // We do not repartition: take number of splits from children
    Integer splitCount = 0;
    for (RelNode input : rel.getInputs()) {
      splitCount += RelMetadataQuery.splitCount(input);
    }
    return splitCount;
  }

}

// End RelMdParallelism.java