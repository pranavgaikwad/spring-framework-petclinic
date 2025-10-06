/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.samples.petclinic.config.BusinessConfig;
import org.springframework.samples.petclinic.config.ToolsConfig;
import org.springframework.samples.petclinic.model.Vet;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Minimal cache behavior test that verifies caching is working correctly.
 * This test should pass both before and after EhCache migration.
 */
@SpringJUnitConfig({BusinessConfig.class, ToolsConfig.class})
@ActiveProfiles("jpa")
class CacheBehaviorTests {

    @Autowired
    private ClinicService clinicService;

    @Autowired
    private CacheManager cacheManager;

    /**
     * Minimal test that verifies:
     * 1. Cache manager is configured
     * 2. findVets() method is actually cached (performance improvement)
     * 3. Data consistency across cached calls
     * 4. Vets cache is created dynamically by JCache
     */
    @Test
    void vetsCachingIsWorking() {
        // Verify cache infrastructure
        assertThat(cacheManager).isNotNull();

        // First call - this should trigger cache creation in JCache
        long start1 = System.nanoTime();
        Collection<Vet> vets1 = clinicService.findVets();
        long duration1 = System.nanoTime() - start1;

        // Verify cache was created after first use
        Cache vetsCache = cacheManager.getCache("vets");
        assertThat(vetsCache).isNotNull();

        // Clear cache to ensure second call is truly cached
        vetsCache.clear();

        // Make another first call after clear
        long start2 = System.nanoTime();
        Collection<Vet> vets2 = clinicService.findVets();
        long duration2 = System.nanoTime() - start2;

        // Third call should be cached
        long start3 = System.nanoTime();
        Collection<Vet> vets3 = clinicService.findVets();
        long duration3 = System.nanoTime() - start3;

        // Verify caching works - compare data content, not object references
        assertThat(vets1).isNotEmpty();
        assertThat(vets2).hasSize(vets1.size());
        assertThat(vets3).hasSize(vets1.size());

        // Verify same vet data is returned (names and IDs should match)
        assertThat(vets2).extracting("id", "firstName", "lastName")
            .containsExactlyInAnyOrderElementsOf(
                vets1.stream().map(v -> org.assertj.core.groups.Tuple.tuple(v.getId(), v.getFirstName(), v.getLastName())).toList()
            );
        assertThat(vets3).extracting("id", "firstName", "lastName")
            .containsExactlyInAnyOrderElementsOf(
                vets1.stream().map(v -> org.assertj.core.groups.Tuple.tuple(v.getId(), v.getFirstName(), v.getLastName())).toList()
            );

        // Verify performance improvement from caching
        assertThat(duration2).isGreaterThan(duration3);
    }
}