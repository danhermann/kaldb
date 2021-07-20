package com.slack.kaldb.metadata.core;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.InvalidProtocolBufferException;
import com.slack.kaldb.metadata.zookeeper.MetadataStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import org.apache.curator.utils.ZKPaths;
import org.slf4j.Logger;

/**
 * KaldbMetadataStore is an abstract class on top of which all the metadata stores will be built.
 *
 * <p>This abstraction is needed so that we can limit the ZK access to the application to a specific
 * set of paths and also to limit the operations that can be performed on those nodes. For example,
 * we only want the SnapshotMetadata to ever be created or deleted but never updated.
 */
abstract class KaldbMetadataStore<T extends KaldbMetadata> {
  protected final MetadataStore metadataStore;
  protected final String storeFolder;
  protected final MetadataSerializer<T> metadataSerializer;
  protected final Logger logger;

  public KaldbMetadataStore(
      MetadataStore metadataStore,
      String storeFolder,
      MetadataSerializer<T> metadataSerializer,
      Logger logger) {
    checkNotNull(metadataStore, "MetadataStore can't be null");
    checkState(
        storeFolder != null && !storeFolder.isEmpty(),
        "SnapshotStoreFolder can't be null or empty.");
    checkNotNull(logger, "Logger can't be null or empty");

    this.metadataStore = metadataStore;
    this.storeFolder = storeFolder;
    this.metadataSerializer = metadataSerializer;
    this.logger = logger;
  }

  protected String getPath(String snapshotName) {
    return ZKPaths.makePath(storeFolder, snapshotName);
  }

  // TODO: byte arrays every where.
  @SuppressWarnings("UnstableApiUsage")
  public ListenableFuture<T> get(String path) {
    String nodePath = getPath(path);
    Function<String, T> deserialize =
        new Function<>() {
          @Override
          public @Nullable T apply(@Nullable String data) {
            T result = null;
            try {
              result = metadataSerializer.fromJsonStr(data);
            } catch (InvalidProtocolBufferException e) {
              final String msg =
                  String.format(
                      "Unable to de-serialize data %s at path %s into a protobuf message.",
                      data, path);
              logger.error(msg, e);
            }
            return result;
          }
        };

    // TODO: Pass in the correct thread pool for this.
    return Futures.transform(
        metadataStore.get(nodePath), deserialize, MoreExecutors.directExecutor());
  }

  @SuppressWarnings("UnstableApiUsage")
  public ListenableFuture<List<T>> list() {
    ListenableFuture<List<String>> children = metadataStore.getChildren(storeFolder);
    Function<List<String>, List<T>> transformFunc =
        new Function<>() {
          @Override
          public @Nullable List<T> apply(@Nullable List<String> paths) {
            if (paths == null) return Collections.emptyList();

            List<ListenableFuture<T>> getFutures = new ArrayList<>(paths.size());
            for (String path : paths) {
              getFutures.add(get(path));
            }
            ListenableFuture<List<T>> response = Futures.successfulAsList(getFutures);
            try {
              return response.get();
            } catch (InterruptedException | ExecutionException e) {
              // TODO: This log may be redundant. If so, remove it.
              logger.error("Encountered Error fetching nodes from metadata store.", e);
              return Collections.emptyList();
            }
          }
        };

    return Futures.transform(children, transformFunc, MoreExecutors.directExecutor());
  }

  public ListenableFuture<?> delete(String path) {
    return metadataStore.delete(getPath(path));
  }
}
