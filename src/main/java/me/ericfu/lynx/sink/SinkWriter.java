package me.ericfu.lynx.sink;

import me.ericfu.lynx.data.RecordBatch;
import me.ericfu.lynx.exception.DataSinkException;
import me.ericfu.lynx.model.checkpoint.SinkCheckpoint;

public interface SinkWriter {

    /**
     * Continue from checkpoint
     */
    void open(SinkCheckpoint checkpoint) throws DataSinkException;

    /**
     * Read next batch of rows from data source
     */
    void writeBatch(RecordBatch batch) throws DataSinkException;

    void close() throws DataSinkException;

    /**
     * Create a latest checkpoint. Returns null if there is nothing to save
     */
    SinkCheckpoint checkpoint();
}
