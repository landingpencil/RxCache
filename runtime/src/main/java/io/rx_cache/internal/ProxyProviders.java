/*
 * Copyright 2015 Victor Albertos
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.rx_cache.internal;

import io.rx_cache.EncryptKey;
import io.rx_cache.Migration;
import io.rx_cache.MigrationCache;
import io.rx_cache.SchemeMigration;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import rx.Observable;
import rx.functions.Func0;

public final class ProxyProviders implements InvocationHandler {
  private final ProcessorProviders processorProviders;
  private final ProxyTranslator proxyTranslator;

  public ProxyProviders(RxCache.Builder builder, Class<?> providersClass) {
    processorProviders = DaggerRxCacheComponent.builder()
        .rxCacheModule(new RxCacheModule(builder.getCacheDirectory(),
            builder.useExpiredDataIfLoaderNotAvailable(),
            builder.getMaxMBPersistenceCache(), getEncryptKey(providersClass),
            getMigrations(providersClass), builder.getJolyglot()))
        .build().providers();

    proxyTranslator = new ProxyTranslator();
  }

  public String getEncryptKey(Class<?> providersClass) {
    EncryptKey encryptKey = providersClass.getAnnotation(EncryptKey.class);
    if (encryptKey == null) return null;
    return encryptKey.value();
  }

  public List<MigrationCache> getMigrations(Class<?> providersClass) {
    List<MigrationCache> migrations = new ArrayList<>();

    Annotation annotation = providersClass.getAnnotation(SchemeMigration.class);
    if (annotation == null) return migrations;

    SchemeMigration schemeMigration = (SchemeMigration) annotation;

    for (Migration migration : schemeMigration.value()) {
      migrations.add(new MigrationCache(migration.version(),
          migration.evictClasses()));
    }

    return migrations;
  }

  @Override public Object invoke(final Object proxy, final Method method, final Object[] args)
      throws Throwable {
    return Observable.defer(new Func0<Observable<Object>>() {
      @Override public Observable<Object> call() {
        return processorProviders.process(proxyTranslator.processMethod(method, args));
      }
    });
  }
}