/*
 * Copyright 2019 Johns Hopkins University
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
package org.dataconservancy.pass.deposit.assembler.shared;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class ExceptionHandlingThreadPoolExecutor extends ThreadPoolExecutor {

    private BiConsumer<Runnable, Throwable> exceptionHandler;

    public ExceptionHandlingThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    public ExceptionHandlingThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime,
                                               TimeUnit unit, BlockingQueue<Runnable> workQueue,
                                               ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    public ExceptionHandlingThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime,
                                               TimeUnit unit, BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
    }

    public ExceptionHandlingThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime,
                                               TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    public BiConsumer<Runnable, Throwable> getExceptionHandler() {
        return exceptionHandler;
    }

    public void setExceptionHandler(BiConsumer<Runnable, Throwable> exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        if (exceptionHandler != null) {
            exceptionHandler.accept(r, t);
        }
    }
}
