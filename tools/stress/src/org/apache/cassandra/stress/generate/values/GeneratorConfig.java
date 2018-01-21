/*
 *
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
 *
 */
package org.apache.cassandra.stress.generate.values;

import java.io.Serializable;
import java.nio.ByteBuffer;

import bd.ac.buet.cse.ms.thesis.FilterSwitch;
import org.apache.cassandra.stress.generate.Distribution;
import org.apache.cassandra.stress.generate.DistributionFactory;
import org.apache.cassandra.utils.ByteBufferUtil;
import org.apache.cassandra.utils.MurmurHash;

public class GeneratorConfig implements Serializable
{
    public final long salt;

    private final DistributionFactory clusteringDistributions;
    private final DistributionFactory sizeDistributions;
    private final DistributionFactory identityDistributions;

    public GeneratorConfig(String seedStr, DistributionFactory clusteringDistributions, DistributionFactory sizeDistributions, DistributionFactory identityDistributions)
    {
        this.clusteringDistributions = clusteringDistributions;
        this.sizeDistributions = sizeDistributions;
        this.identityDistributions = identityDistributions;
        ByteBuffer buf = ByteBufferUtil.bytes(seedStr);
        long[] hash = new long[2];
        MurmurHash.hash3_x64_128(buf, buf.position(), buf.remaining(), 0, hash);
        if (FilterSwitch.filter == FilterSwitch.CUCKOO_FILTER) {
            throw new RuntimeException("ERROR: CuckooHashing is not used here! See the source code line above this one.");
        }
        salt = hash[0];
    }

    Distribution getClusteringDistribution(DistributionFactory deflt)
    {
        return (clusteringDistributions == null ? deflt : clusteringDistributions).get();
    }

    Distribution getIdentityDistribution(DistributionFactory deflt)
    {
        return (identityDistributions == null ? deflt : identityDistributions).get();
    }

    Distribution getSizeDistribution(DistributionFactory deflt)
    {
        return (sizeDistributions == null ? deflt : sizeDistributions).get();
    }

    public String getConfigAsString()
    {
        StringBuilder sb = new StringBuilder();
        if (clusteringDistributions != null){
            sb.append(String.format("Clustering: %s;", clusteringDistributions.getConfigAsString()));
        }
        if (sizeDistributions != null){
            sb.append(String.format("Size: %s;", sizeDistributions.getConfigAsString()));
        }
        if (identityDistributions != null){
            sb.append(String.format("Identity: %s;", identityDistributions.getConfigAsString()));
        }
        return sb.toString();
    }
}
