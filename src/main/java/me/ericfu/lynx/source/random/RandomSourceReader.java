package me.ericfu.lynx.source.random;

import lombok.Data;
import me.ericfu.lynx.data.Record;
import me.ericfu.lynx.data.RecordBatch;
import me.ericfu.lynx.data.RecordBatchBuilder;
import me.ericfu.lynx.data.RecordBuilder;
import me.ericfu.lynx.exception.DataSourceException;
import me.ericfu.lynx.model.checkpoint.SourceCheckpoint;
import me.ericfu.lynx.schema.Field;
import me.ericfu.lynx.source.SourceReader;

import java.util.Random;

class RandomSourceReader implements SourceReader {

    private static final int RAND_STRING_LENGTH = 10;

    private final RandomSource s;
    private final RandomSourceTable table;
    private final int end;
    private int current;

    private RecordBatchBuilder builder;
    private Random random;
    private RandomGenerator[] generators;

    public RandomSourceReader(RandomSource s, RandomSourceTable table, int start, int end) {
        this.s = s;
        this.table = table;
        this.current = start;
        this.end = end;
    }

    @Override
    public void open() throws DataSourceException {
        this.builder = new RecordBatchBuilder(s.globals.getBatchSize());
        this.random = new Random();

        this.generators = new RandomGenerator[table.getType().getFieldCount()];
        for (int i = 0; i < table.getType().getFieldCount(); i++) {
            Field field = table.getType().getField(i);

            // Find the matched column rule and read the rule code
            String code = null;
            if (s.conf.getColumns() != null) {
                for (RandomSourceConf.RandomRule r : s.conf.getColumns().get(table.getName())) {
                    if (r.getName().equals(field.getName())) {
                        if (r.getRule() != null) {
                            code = r.getRule();
                            break;
                        }
                    }
                }
            }

            if (code == null) {
                generators[i] = createDefaultGenerator(field);
            } else {
                generators[i] = new RandomGeneratorCompiler().compile(code, field.getType().getClazz());
            }
        }
    }

    @Override
    public void open(SourceCheckpoint checkpoint) throws DataSourceException {
        open();

        Checkpoint cp = (Checkpoint) checkpoint;
        this.current = cp.nextRowNum;
    }

    @Override
    public RecordBatch readBatch() throws DataSourceException {
        for (int i = 0; i < s.globals.getBatchSize() && current < end; i++, current++) {
            builder.addRow(buildRandomRecord());
        }

        if (builder.size() > 0) {
            return builder.buildAndReset();
        } else {
            return null;
        }
    }

    private Record buildRandomRecord() {
        RecordBuilder builder = new RecordBuilder(table.getType());
        for (int i = 0; i < table.getType().getFieldCount(); i++) {
            builder.set(i, generators[i].generate(current + 1, random));
        }
        return builder.build();
    }

    private static RandomGenerator createDefaultGenerator(Field field) {
        switch (field.getType()) {
        case BOOLEAN:
            return (i, r) -> r.nextBoolean();
        case INT:
            return (i, r) -> r.nextInt();
        case LONG:
            return (i, r) -> r.nextLong();
        case FLOAT:
            return (i, r) -> r.nextFloat();
        case DOUBLE:
            return (i, r) -> r.nextDouble();
        case STRING:
            return (i, r) -> RandomUtils.randomAsciiString(r, RAND_STRING_LENGTH);
        case BINARY:
            return (i, r) -> RandomUtils.randomBinary(r, RAND_STRING_LENGTH);
        default:
            throw new AssertionError();
        }
    }

    @Override
    public void close() throws DataSourceException {
        // do nothing
    }

    @Override
    public SourceCheckpoint checkpoint() {
        Checkpoint cp = new Checkpoint();
        cp.setNextRowNum(current);
        return cp;
    }

    @Data
    public static class Checkpoint implements SourceCheckpoint {
        private int nextRowNum;
    }
}
