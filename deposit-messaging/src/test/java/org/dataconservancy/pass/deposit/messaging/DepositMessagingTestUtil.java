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
package org.dataconservancy.pass.deposit.messaging;

import java.net.URI;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.dataconservancy.pass.model.Deposit.DepositStatus;
import org.dataconservancy.pass.model.Submission.AggregatedDepositStatus;
import org.dataconservancy.pass.model.Submission.SubmissionStatus;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DepositMessagingTestUtil {

    private DepositMessagingTestUtil() {
    }

    private static final Random RANDOM = new Random();

    /**
     * Supplies a random AggregatedDepositStatus
     */
    public static Supplier<AggregatedDepositStatus> randomAggregatedDepositStatus =
        DepositMessagingTestUtil::randomAggregateDepositStatus;

    /**
     * Supplies a random terminal AggregatedDepositStatus
     */
    public static Supplier<AggregatedDepositStatus> randomTerminalAggregatedDepositStatus =
        () -> randomAggregatedDepositStatusExcept(
            AggregatedDepositStatus.FAILED,
            AggregatedDepositStatus.IN_PROGRESS,
            AggregatedDepositStatus.NOT_STARTED);

    /**
     * Supplies a random intermediate AggregatedDepositStatus
     */
    public static Supplier<AggregatedDepositStatus> randomIntermediateAggregatedDepositStatus =
        () -> randomAggregatedDepositStatusExcept(
            AggregatedDepositStatus.REJECTED, AggregatedDepositStatus.ACCEPTED);

    /**
     * Supplies a random DepositStatus
     */
    public static Supplier<DepositStatus> randomDepositStatus =
        DepositMessagingTestUtil::randomDepositStatus;

    /**
     * Supplies a random terminal DepositStatus
     */
    public static Supplier<DepositStatus> randomTerminalDepositStatus =
        () -> randomDepositStatusExcept(DepositStatus.SUBMITTED, DepositStatus.FAILED);

    /**
     * Supplies a random intermediate DepositStatus
     */
    public static Supplier<DepositStatus> randomIntermediateDepositStatus =
        () -> randomDepositStatusExcept(DepositStatus.REJECTED, DepositStatus.ACCEPTED);

    /**
     * Generates a random {@link SubmissionStatus}
     *
     * @return
     */
    public static SubmissionStatus randomSubmissionStatus() {
        return random(SubmissionStatus.class);
    }

    /**
     * Generates a random {@link SubmissionStatus}, but the returned status is guaranteed not to include any
     * status present in {@code excludeStatus}.
     *
     * @param excludes excluded from the possible returns
     * @return
     */
    public static SubmissionStatus randomSubmissionStatusExcept(SubmissionStatus... excludes) {
        return random(SubmissionStatus.class, excludes);
    }

    /**
     * Generates a random {@link DepositStatus}
     *
     * @return
     */
    public static DepositStatus randomDepositStatus() {
        return random(DepositStatus.class);
    }

    /**
     * Generates a random {@link DepositStatus}, but the returned status is guaranteed not to include any
     * status present in {@code excludeStatus}.
     *
     * @param excludes excluded from the possible returns
     * @return
     */
    public static DepositStatus randomDepositStatusExcept(DepositStatus... excludes) {
        return random(DepositStatus.class, excludes);
    }

    /**
     * Generates a random {@link DepositStatus}, but the returned status is guaranteed not to include any status
     * present in {@code excludeStatus}.
     *
     * @param excludes excluded from the possible returns
     * @return
     */
    public static DepositStatus randomDepositStatusExcept(Predicate<DepositStatus> excludes) {
        return random(DepositStatus.class, excludes);
    }

    /**
     * Generates a random {@link AggregatedDepositStatus}
     *
     * @return
     */
    public static AggregatedDepositStatus randomAggregateDepositStatus() {
        return random(AggregatedDepositStatus.class);
    }

    /**
     * Generates a random {@link AggregatedDepositStatus}, but the returned status is guaranteed not to
     * include any status present in {@code excludeStatus}.
     *
     * @param excludes excluded from the possible returns
     * @return
     */
    public static AggregatedDepositStatus randomAggregatedDepositStatusExcept(
        AggregatedDepositStatus... excludes) {
        return random(AggregatedDepositStatus.class, excludes);
    }

    /**
     * Generates a random {@link AggregatedDepositStatus}, but the returned status is guaranteed not to
     * include any status present in {@code excludeStatus}.
     *
     * @param excludes excluded from the possible returns
     * @return
     */
    public static AggregatedDepositStatus randomAggregatedDepositStatusExcept(
        Predicate<AggregatedDepositStatus> excludes) {
        return random(AggregatedDepositStatus.class, excludes);
    }

    /**
     * Returns a random value from the supplied Enum class.
     *
     * @param clazz
     * @param <T>
     * @return
     */
    public static <T extends Enum<T>> T random(Class<T> clazz) {
        Enum anEnum = randomFromEnum(clazz);
        return T.valueOf(clazz, anEnum.name());
    }

    /**
     * Returns a random value from the supplied Enum class, excluding the supplied enums.
     *
     * @param clazz
     * @param excludes
     * @param <T>
     * @return
     */
    @SafeVarargs
    public static <T extends Enum<T>> T random(Class<T> clazz, T... excludes) {
        Predicate<String> excludesPredicate =
            (statusName) -> Stream.of(excludes).anyMatch(toExclude -> toExclude.name().equals(statusName));
        Enum anEnum = randomFromEnumExcludes(clazz, excludesPredicate);
        return T.valueOf(clazz, anEnum.name());
    }

    /**
     * Returns a random value from the supplied Enum class, excluding those matching the predicate.
     *
     * @param clazz
     * @param excludes
     * @param <T>
     * @return
     */
    public static <T extends Enum<T>> T random(Class<T> clazz, Predicate<T> excludes) {
        Enum anEnum = randomFromEnumExcludesPredicate(clazz, excludes);
        return T.valueOf(clazz, anEnum.name());
    }

    /**
     * Generates a random URI
     *
     * @return
     */
    public static URI randomUri() {
        return URI.create("uri:uuid:" + UUID.randomUUID());
    }

    private static <T extends Enum<T>> T randomFromEnum(Class<T> enumClass) {
        T[] values = enumClass.getEnumConstants();
        return values[RANDOM.nextInt(values.length)];
    }

    private static <T extends Enum<T>> Enum randomFromEnumExcludes(Class<T> enumClass, Predicate<String> excludes) {
        AtomicReference<Enum> result = new AtomicReference<>();
        do {
            result.set(randomFromEnum(enumClass));
        } while (excludes.test(result.get().name()));

        return result.get();
    }

    private static <T extends Enum<T>> Enum randomFromEnumExcludesPredicate(Class<T> enumClass, Predicate<T> excludes) {
        AtomicReference<T> result = new AtomicReference<>();
        do {
            result.set(randomFromEnum(enumClass));
        } while (excludes.test(result.get()));

        return result.get();
    }

}
