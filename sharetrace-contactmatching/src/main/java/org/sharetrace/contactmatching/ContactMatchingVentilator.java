package org.sharetrace.contactmatching;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.AWSLambdaAsync;
import com.amazonaws.services.lambda.AWSLambdaAsyncClientBuilder;
import com.amazonaws.services.lambda.model.InvocationType;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.sharetrace.lambda.common.Ventilator;
import org.sharetrace.lambda.common.util.HandlerUtil;
import org.sharetrace.model.location.LocationHistory;
import org.sharetrace.model.util.ShareTraceUtil;

/*
TODO Finalize event type for input
TODO Add the functionality to take MULTIPLE pairs of unique entries to handle per worker payload.
 This allows the flexibility of "combining" many small files written earlier by the ReadWorker into fewer, larger files.
 */
public class ContactMatchingVentilator implements Ventilator<LocationHistory>,
    RequestHandler<ScheduledEvent, String> {

  // Logging messages
  private static final String FAILED_TO_SERIALIZE_MSG = "Failed to serialize request body: \n";
  private static final String CANNOT_FIND_ENV_VAR_MSG = "Unable to environment variable: \n";
  private static final String NO_WORKERS_MSG = "No worker functions were found. Exiting handler";
  private static final String CANNOT_DESERIALIZE = "Unable to deserialize: \n";
  private static final String NO_PARTITIONS_MSG = "No partitions to process. Exiting";
  private static final String UNABLE_TO_LOAD_PARTITION = "Unable to load partition from S3: \n";

  // Environment variable keys
  private static final String FIRST_WORKER = "matchWorker1";
  private static final String SECOND_WORKER = "matchWorker2";
  private static final List<String> WORKER_KEYS = ImmutableList.of(FIRST_WORKER, SECOND_WORKER);

  private static final String SOURCE_BUCKET = "sharetrace-locations";

  private static final AmazonS3 S3 = AmazonS3ClientBuilder.standard()
      .withRegion(Regions.US_EAST_2).build();
  private static final AWSLambdaAsync LAMBDA = AWSLambdaAsyncClientBuilder.standard()
      .withRegion(Regions.US_EAST_2).build();

  private static final ObjectMapper MAPPER = ShareTraceUtil.getMapper();

  private static final ContactMatchingComputation COMPUTATION = new ContactMatchingComputation();

  private LambdaLogger logger;

  @Override
  public String handleRequest(ScheduledEvent input, Context context) {
    HandlerUtil.logEnvironment(input, context);
    logger = context.getLogger();
    handleRequest();
    return HandlerUtil.get200Ok();
  }

  @Override
  public void handleRequest() {
    List<S3ObjectSummary> objects = getObjects();
    List<Map.Entry<Integer, Integer>> entries = getUniqueEntries(objects.size());
    List<String> workers = getWorkers();
    int nWorkers = workers.size();
    int nEntries = entries.size();
    for (int iPartition = 0; iPartition < nEntries; iPartition++) {
      Map.Entry<Integer, Integer> pair = entries.get(iPartition);
      int iObject = pair.getKey();
      int jObject = pair.getValue();
      S3Object object = S3.getObject(SOURCE_BUCKET, objects.get(iObject).getKey());
      S3Object otherObject = S3.getObject(SOURCE_BUCKET, objects.get(jObject).getKey());
      String worker = workers.get(iPartition % nWorkers);
      List<LocationHistory> payload = mapObjects(object, otherObject);
      invokeWorker(worker, payload);
    }
  }

  private List<S3ObjectSummary> getObjects() {
    List<S3ObjectSummary> objects = S3.listObjectsV2(SOURCE_BUCKET).getObjectSummaries();
    if (objects.isEmpty()) {
      logger.log(NO_PARTITIONS_MSG);
      System.exit(1);
    }
    return objects;
  }

  public List<Map.Entry<Integer, Integer>> getUniqueEntries(int nObjects) {
    Set<Entry<Integer, Integer>> uniqueEntries = COMPUTATION.getUniqueEntries(nObjects);
    if (uniqueEntries.isEmpty()) {
      uniqueEntries = ImmutableSet.of(new SimpleImmutableEntry<>(0, 0));
    }
    return ImmutableList.copyOf(uniqueEntries);
  }

  @Override
  public List<String> getWorkers() {
    List<String> workers = WORKER_KEYS.stream()
        .map(this::getEnvironmentVariable)
        .collect(Collectors.toList());
    if (workers.isEmpty()) {
      logger.log(NO_WORKERS_MSG);
      System.exit(1);
    }
    return workers;
  }

  private String getEnvironmentVariable(String key) {
    String value = null;
    try {
      value = HandlerUtil.getEnvironmentVariable(key);
    } catch (NullPointerException e) {
      logger.log(CANNOT_FIND_ENV_VAR_MSG + e.getMessage());
    }
    return value;
  }

  private List<LocationHistory> mapObjects(S3Object o1, S3Object o2) {
    return ImmutableList.<LocationHistory>builder()
        .addAll(loadPartition(o1))
        .addAll(loadPartition(o2))
        .build();
  }

  private Set<LocationHistory> loadPartition(S3Object object) {
    Set<LocationHistory> partition = ImmutableSet.of();
    S3ObjectInputStream input = object.getObjectContent();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, Charsets.UTF_8))) {
      partition = reader.lines()
          .map(this::mapToLocationHistory)
          .filter(Objects::nonNull)
          .collect(Collectors.toSet());
      input.close();
    } catch (IOException e) {
      input.abort();
      HandlerUtil.logException(logger, e, UNABLE_TO_LOAD_PARTITION);
    }
    return ImmutableSet.copyOf(partition);
  }

  private LocationHistory mapToLocationHistory(String s) {
    LocationHistory mapped = null;
    try {
      mapped = MAPPER.readValue(s, LocationHistory.class);
    } catch (JsonProcessingException e) {
      HandlerUtil.logException(logger, e, CANNOT_DESERIALIZE);
    }
    return mapped;
  }

  @Override
  public void invokeWorker(String worker, Collection<LocationHistory> payload) {
    try {
      InvokeRequest invokeRequest = new InvokeRequest()
          .withFunctionName(worker)
          .withInvocationType(InvocationType.Event)
          .withPayload(MAPPER.writeValueAsString(payload));
      LAMBDA.invokeAsync(invokeRequest);
    } catch (JsonProcessingException e) {
      HandlerUtil.logException(logger, e, FAILED_TO_SERIALIZE_MSG);
    }
  }
}
