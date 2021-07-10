package com.slack.kaldb.metadata.snapshot;

import com.slack.kaldb.metadata.core.PersistentCreatableMetadataStore;
import com.slack.kaldb.metadata.zookeeper.MetadataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnapshotMetadataStore extends PersistentCreatableMetadataStore<SnapshotMetadata> {

  private static final Logger LOG = LoggerFactory.getLogger(SnapshotMetadataStore.class);

  // TODO: Add a setup method to initialize the store?

  // TODO: Add listeners to the store and start cache.
  public SnapshotMetadataStore(
      MetadataStore metadataStore, String snapshotStorePath, boolean shouldCache) throws Exception {
    super(shouldCache, snapshotStorePath, metadataStore, new SnapshotMetadataSerializer(), LOG);
  }
}
