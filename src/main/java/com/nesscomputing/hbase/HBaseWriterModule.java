/**
 * Copyright (C) 2012 Ness Computing, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nesscomputing.hbase;

import static java.lang.String.format;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

import com.nesscomputing.config.ConfigProvider;
import com.nesscomputing.hbase.spill.SpillController;
import com.nesscomputing.hbase.spill.SpillReader;
import com.nesscomputing.lifecycle.LifecycleStage;
import com.nesscomputing.lifecycle.guice.AbstractLifecycleProvider;
import com.nesscomputing.lifecycle.guice.LifecycleAction;

import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.weakref.jmx.guice.MBeanModule;
/**
 * Defines a new HBase writer. Each writer can have its own configuration.
 */
public class HBaseWriterModule extends AbstractModule
{
    private final String writerName;

    public HBaseWriterModule(final String writerName)
    {
        Preconditions.checkState(!StringUtils.isBlank(writerName), "Writer name must not be blank!");

        this.writerName = writerName;
    }

    @Override
    protected void configure()
    {
        final Named named = Names.named(writerName);
        bind(HBaseWriterConfig.class).annotatedWith(named).toProvider(ConfigProvider.of(HBaseWriterConfig.class, ImmutableMap.of("writername", writerName))).in(Scopes.SINGLETON);
        bind(HBaseWriter.class).annotatedWith(named).toProvider(new HBaseWriterProvider(named)).asEagerSingleton();
        bind(SpillController.class).annotatedWith(named).toProvider(new SpillControllerProvider(named)).in(Scopes.SINGLETON);
        bind(SpillReader.class).annotatedWith(named).toProvider(new SpillReaderProvider(named)).asEagerSingleton();

        install(new MBeanModule() {
            @Override
            public void configureMBeans() {
                export(HBaseWriter.class).annotatedWith(named).as(format("ness.hbase.writer:name=%s", writerName));
                export(SpillController.class).annotatedWith(named).as(format("ness.hbase.spill:name=%s", writerName));
            }
        });
    }

    public static class HBaseWriterProvider extends AbstractLifecycleProvider<HBaseWriter> implements Provider<HBaseWriter>
    {
        private final Named named;
        private HBaseWriterConfig writerConfig = null;
        private Configuration hadoopConfig = null;
        private SpillController spillController = null;

        private HBaseWriterProvider(final Named named)
        {
            this.named = named;

            addAction(LifecycleStage.START_STAGE, new LifecycleAction<HBaseWriter>() {
                    @Override
                    public void performAction(final HBaseWriter hbaseWriter) {
                        hbaseWriter.start();
                    }
                });

            addAction(LifecycleStage.STOP_STAGE, new LifecycleAction<HBaseWriter>() {
                    @Override
                    public void performAction(final HBaseWriter hbaseWriter) {
                        hbaseWriter.stop();
                    }
                });
        }

        @Inject
        void setInjector(final Injector injector)
        {
            this.hadoopConfig = injector.getInstance(Configuration.class);

            this.writerConfig = injector.getInstance(Key.get(HBaseWriterConfig.class, named));
            this.spillController = injector.getInstance(Key.get(SpillController.class, named));

        }

        @Override
        public HBaseWriter internalGet()
        {
            Preconditions.checkState(writerConfig != null, "no writerConfig was injected!");
            return new HBaseWriter(writerConfig, hadoopConfig, spillController);
        }
    }

    public static class SpillControllerProvider implements Provider<SpillController>
    {
        private final Named named;
        private HBaseWriterConfig writerConfig = null;

        public SpillControllerProvider(final Named named)
        {
            this.named = named;
        }

        @Inject
        void setInjector(final Injector injector)
        {
            this.writerConfig = injector.getInstance(Key.get(HBaseWriterConfig.class, named));
        }

        @Override
        public SpillController get()
        {
            return new SpillController(named.value(), writerConfig);
        }
    }

    public static class SpillReaderProvider extends AbstractLifecycleProvider<SpillReader> implements Provider<SpillReader>
    {
        private final Named named;
        private HBaseWriterConfig writerConfig = null;
        private Configuration hadoopConfig = null;
        private SpillController spillController = null;

        private SpillReaderProvider(final Named named)
        {
            this.named = named;

            addAction(LifecycleStage.START_STAGE, new LifecycleAction<SpillReader>() {
                    @Override
                    public void performAction(final SpillReader spillReader) {
                        spillReader.start();
                    }
                });

            addAction(LifecycleStage.STOP_STAGE, new LifecycleAction<SpillReader>() {
                    @Override
                    public void performAction(final SpillReader spillReader) {
                        spillReader.stop();
                    }
                });
        }

        @Inject
        void setInjector(final Injector injector)
        {
            this.hadoopConfig = injector.getInstance(Configuration.class);

            this.writerConfig = injector.getInstance(Key.get(HBaseWriterConfig.class, named));
            this.spillController = injector.getInstance(Key.get(SpillController.class, named));

        }

        @Override
        public SpillReader internalGet()
        {
            Preconditions.checkState(writerConfig != null, "no writerConfig was injected!");
            return new SpillReader(writerConfig, hadoopConfig, spillController);
        }
    }


}
