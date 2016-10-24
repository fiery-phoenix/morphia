package org.mongodb.morphia.query;


import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.mongodb.morphia.PathTarget;
import org.mongodb.morphia.mapping.MappedClass;
import org.mongodb.morphia.mapping.MappedField;
import org.mongodb.morphia.mapping.Mapper;
import org.mongodb.morphia.mapping.MappingException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.mongodb.morphia.MorphiaUtils.join;
import static org.mongodb.morphia.query.QueryValidator.validateQuery;


/**
 * @param <T> the type to update
 * @author Scott Hernandez
 */
public class UpdateOpsImpl<T> implements UpdateOperations<T> {
    private final Mapper mapper;
    private final Class<T> clazz;
    private Map<String, Map<String, Object>> ops = new HashMap<String, Map<String, Object>>();
    private boolean validateNames = true;
    private boolean validateTypes = true;
    private boolean isolated;

    /**
     * Creates an UpdateOpsImpl for the type given.
     *
     * @param type   the type to update
     * @param mapper the Mapper to use
     */
    public UpdateOpsImpl(final Class<T> type, final Mapper mapper) {
        this.mapper = mapper;
        clazz = type;
    }

    @Override
    @Deprecated
    public UpdateOperations<T> add(final String field, final Object value) {
        return addToSet(field, value);
    }

    @Override
    @Deprecated
    public UpdateOperations<T> add(final String field, final Object value, final boolean addDups) {
        if (value == null) {
            throw new QueryException("Value cannot be null.");
        }

        if (addDups) {
            List<?> values = value instanceof List ? (List<?>) value : singletonList(value);
            push(field, values);
        } else {
            List<?> values = value instanceof List ? (List<?>) value : singletonList(value);
            addToSet(field, values);
        }
        return this;
    }

    @Override
    @Deprecated
    public UpdateOperations<T> addAll(final String field, final List<?> values, final boolean addDups) {
        if (values == null || values.isEmpty()) {
            throw new QueryException("Values cannot be null or empty.");
        }

        return (addDups) ? push(field, values) : addToSet(field, values);
    }

    @Override
    public UpdateOperations<T> addToSet(final String field, final Object value) {
        if (value == null) {
            throw new QueryException("Value cannot be null.");
        }

        add(UpdateOperator.ADD_TO_SET, field, value, true);
        return this;
    }

    @Override
    public UpdateOperations<T> addToSet(final String field, final List<?> values) {
        if (values == null || values.isEmpty()) {
            throw new QueryException("Values cannot be null or empty.");
        }

        add(UpdateOperator.ADD_TO_SET_EACH, field, values, true);
        return this;
    }

    @Override
    public UpdateOperations<T> push(final String field, final Object value) {
        return push(field, value instanceof List ? (List<?>) value : singletonList(value), new PushOptions());
    }

    @Override
    public UpdateOperations<T> push(final String field, final Object value, final PushOptions options) {
        return push(field, value instanceof List ? (List<?>) value : singletonList(value), options);
    }

    @Override
    public UpdateOperations<T> push(final String field, final List<?> values) {
        return push(field, values, new PushOptions());
    }

    @Override
    public UpdateOperations<T> push(final String field, final List<?> values, final PushOptions options) {
        if (values == null || values.isEmpty()) {
            throw new QueryException("Values cannot be null or empty.");
        }

        PathTarget pathTarget = new PathTarget(mapper, mapper.getMappedClass(clazz), field);
        if (!validateNames) {
            pathTarget.disableValidation();
        }
        MappedField mf = pathTarget.getTarget();

        BasicDBObject dbObject = new BasicDBObject(UpdateOperator.EACH.val(), mapper.toMongoObject(mf, null, values));
        options.update(dbObject);
        addOperation(UpdateOperator.PUSH, pathTarget.translatedPath(), dbObject);

        return this;
    }

    @Override
    public UpdateOperations<T> dec(final String field) {
        return inc(field, -1);
    }

    @Override
    public UpdateOperations<T> disableValidation() {
        validateNames = false;
        validateTypes = false;
        return this;
    }

    @Override
    public UpdateOperations<T> enableValidation() {
        validateNames = true;
        validateTypes = true;
        return this;
    }

    @Override
    public UpdateOperations<T> inc(final String field) {
        return inc(field, 1);
    }

    @Override
    public UpdateOperations<T> inc(final String field, final Number value) {
        if (value == null) {
            throw new QueryException("Value cannot be null.");
        }
        add(UpdateOperator.INC, field, value, false);
        return this;
    }

    @Override
    public UpdateOperations<T> isolated() {
        isolated = true;
        return this;
    }

    @Override
    public UpdateOperations<T> max(final String field, final Number value) {
        add(UpdateOperator.MAX, field, value, false);
        return this;
    }

    @Override
    public UpdateOperations<T> min(final String field, final Number value) {
        add(UpdateOperator.MIN, field, value, false);
        return this;
    }

    @Override
    public UpdateOperations<T> removeAll(final String field, final Object value) {
        if (value == null) {
            throw new QueryException("Value cannot be null.");
        }
        add(UpdateOperator.PULL, field, value, true);
        return this;
    }

    @Override
    public UpdateOperations<T> removeAll(final String field, final List<?> values) {
        if (values == null || values.isEmpty()) {
            throw new QueryException("Value cannot be null or empty.");
        }

        add(UpdateOperator.PULL_ALL, field, values, true);
        return this;
    }

    @Override
    public UpdateOperations<T> removeFirst(final String field) {
        return remove(field, true);
    }

    @Override
    public UpdateOperations<T> removeLast(final String field) {
        return remove(field, false);
    }

    @Override
    public UpdateOperations<T> set(final String field, final Object value) {
        if (value == null) {
            throw new QueryException("Value cannot be null.");
        }

        add(UpdateOperator.SET, field, value, true);
        return this;
    }

    @Override
    public UpdateOperations<T> setOnInsert(final String field, final Object value) {
        if (value == null) {
            throw new QueryException("Value cannot be null.");
        }

        add(UpdateOperator.SET_ON_INSERT, field, value, true);
        return this;
    }

    @Override
    public UpdateOperations<T> unset(final String field) {
        add(UpdateOperator.UNSET, field, 1, false);
        return this;
    }

    /**
     * @return the operations listed
     */
    public DBObject getOps() {
        return new BasicDBObject(ops);
    }

    /**
     * Sets the operations for this UpdateOpsImpl
     *
     * @param ops the operations
     */
    @SuppressWarnings("unchecked")
    public void setOps(final DBObject ops) {
        this.ops = (Map<String, Map<String, Object>>) ops;
    }

    /**
     * @return true if isolated
     */
    public boolean isIsolated() {
        return isolated;
    }

    //TODO Clean this up a little.
    protected void add(final UpdateOperator op, final String f, final Object value, final boolean convert) {
        if (value == null) {
            throw new QueryException("Val cannot be null");
        }

        Object val = value;
        PathTarget pathTarget = new PathTarget(mapper, mapper.getMappedClass(clazz), f);
        if (!validateNames) {
            pathTarget.disableValidation();
        }
        pathTarget.resolve();
        MappedField mf = pathTarget.getTarget();

        if (convert) {
            if (UpdateOperator.PULL_ALL.equals(op) && value instanceof List) {
                val = toDBObjList(mf, (List<?>) value);
            } else {
                val = mapper.toMongoObject(mf, null, value);
            }
        }


        if (UpdateOperator.ADD_TO_SET_EACH.equals(op)) {
            val = new BasicDBObject(UpdateOperator.EACH.val(), val);
        }

        addOperation(op, pathTarget.translatedPath(), val);
    }

    private void addOperation(final UpdateOperator op, final String fieldName, final Object val) {
        final String opString = op.val();

        if (!ops.containsKey(opString)) {
            ops.put(opString, new LinkedHashMap<String, Object>());
        }
        ops.get(opString).put(fieldName, val);
    }

    private MappedField validate(final Object val, final StringBuilder fieldName) {
        return validateNames || validateTypes
               ? validateQuery(clazz, mapper, fieldName, FilterOperator.EQUAL, val, validateNames, validateTypes)
               : null;
    }

    MappedField findField(final MappedClass mc, final List<String> path) {
        String segment = path.get(0);

        MappedField mf = mc.getMappedField(segment);
        if (mf == null) {
            mf = mc.getMappedFieldByJavaField(segment);
        }
        if (mf == null && mc.isInterface()) {
            for (final MappedClass mappedClass : mapper.getSubTypes(mc)) {
                try {
                    return findField(mappedClass, new ArrayList<String>(path));
                } catch (MappingException e) {
                    // try the next one
                }
            }
        }
        String namePath;
        if (mf != null) {
            namePath = mf.getNameToStore();
        } else {
            if (!validateNames) {
                throw new MappingException(format("Could not resolve path '%s' against '%s'.", join(path, '.'), mc.getClazz().getName()));
            } else {
                return null;
            }
        }
        if (path.size() > 1) {
            try {
                Class concreteType = !mf.isSingleValue() ? mf.getSubClass() : mf.getConcreteType();
                namePath += "." + findField(mapper.getMappedClass(concreteType), path.subList(1, path.size()));
            } catch (MappingException e) {
                if (!validateNames) {
                    throw new MappingException(format("Could not resolve path '%s' against '%s'.", join(path, '.'), mc.getClazz().getName()));
                } else {
                    return null;
                }
            }
        }
        return mf;
    }

    protected UpdateOperations<T> remove(final String fieldExpr, final boolean firstNotLast) {
        add(UpdateOperator.POP, fieldExpr, (firstNotLast) ? -1 : 1, false);
        return this;
    }

    protected List<Object> toDBObjList(final MappedField mf, final List<?> values) {
        final List<Object> list = new ArrayList<Object>(values.size());
        for (final Object obj : values) {
            list.add(mapper.toMongoObject(mf, null, obj));
        }

        return list;
    }

}
