package me.ericfu.lynx.sink.jdbc;

import me.ericfu.lynx.exception.DataSinkException;
import me.ericfu.lynx.model.conf.GeneralConf;
import me.ericfu.lynx.schema.*;
import me.ericfu.lynx.sink.Sink;
import me.ericfu.lynx.sink.SinkWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public class JdbcSink implements Sink {

    private static final Logger logger = LoggerFactory.getLogger(JdbcSink.class);

    final GeneralConf globals;
    final JdbcSinkConf conf;

    Schema schema;

    /**
     * Insert statement templates for each table
     */
    Map<String, String> insertTemplates;

    Properties connProps;

    public JdbcSink(GeneralConf globals, JdbcSinkConf conf) {
        this.globals = globals;
        this.conf = conf;
    }

    @Override
    public void init() throws DataSinkException {
        // build connection properties
        connProps = new Properties();
        if (conf.getUser() != null) {
            connProps.put("user", conf.getUser());
        }
        if (conf.getPassword() != null) {
            connProps.put("password", conf.getPassword());
        }
        if (conf.getProperties() != null) {
            connProps.putAll(conf.getProperties());
        }

        // Fetch schema via JDBC metadata interface
        Map<String, RecordTypeBuilder> recordTypeBuilders = new HashMap<>();
        try (Connection connection = DriverManager.getConnection(conf.getUrl(), connProps)) {
            // Extract schema from target table
            DatabaseMetaData meta = connection.getMetaData();
            try (ResultSet rs = meta.getColumns(null, null, "%", null)) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String columnName = rs.getString("COLUMN_NAME");
                    int jdbcType = rs.getInt("DATA_TYPE");
                    BasicType dataType = JdbcUtils.convertJdbcType(jdbcType);

                    RecordTypeBuilder r = recordTypeBuilders.computeIfAbsent(tableName, t -> new RecordTypeBuilder());
                    r.addField(columnName, dataType);
                }
            }
        } catch (SQLException ex) {
            throw new DataSinkException("cannot fetch metadata", ex);
        }

        SchemaBuilder schemaBuilder = new SchemaBuilder();
        recordTypeBuilders.forEach((name, builder) -> {
            Table table = new Table(name, builder.build());
            schemaBuilder.addTable(table);
        });
        schema = schemaBuilder.build();

        // build insert statement template
        insertTemplates = schema.getTables().stream()
            .collect(Collectors.toMap(Table::getName, this::buildInsertTemplate));
    }

    @Override
    public Schema getSchema() {
        return schema;
    }

    @Override
    public SinkWriter createWriter(Table table) {
        return new JdbcSinkWriter(this, table);
    }

    private String buildInsertTemplate(Table table) {
        String fieldList = table.getType().getFields().stream().map(Field::getName)
            .collect(Collectors.joining(",", "(", ")"));
        String valueList = table.getType().getFields().stream().map(x -> "?")
            .collect(Collectors.joining(",", "(", ")"));
        return "INSERT IGNORE INTO " + table.getName() + fieldList + " VALUES " + valueList;
    }
}
