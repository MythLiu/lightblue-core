/*
 Copyright 2013 Red Hat, Inc. and/or its affiliates.

 This file is part of lightblue.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.redhat.lightblue.crud;

import java.util.Iterator;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.function.Consumer;

/**
 * This interface is used to stream documents. A call to getDocuments
 * should return an iterator of the document resultset.
 */
public interface DocumentStream<T> extends Iterator<T>{
    /**
     * Close the document stream
     */
    void close();

    /**
     * Adds a listener that will be called for each document of the stream when next() is called
     */
    void tee(Consumer<T> dest);

    /**
     * returns true if rewind works
     */
    default boolean canRewind() { return false; }

    /**
     * returns a new stream that starts the same resultset from the beginning. Only works if canRewind() is true
     */
    default DocumentStream<T> rewind() { throw new UnsupportedOperationException(); }

    public static <S,D> DocumentStream<D> map(final DocumentStream<S> source,final Function<S,D> map) {
        return new DocumentStreamMapper<S,D>(source,map);
    }

    static final class DocumentStreamMapper<S,D> implements DocumentStream<D> {
        private final ArrayList<Consumer<D>> listeners=new ArrayList<>();
        private final DocumentStream<S> source;
        private final Function<S,D> map;
        DocumentStreamMapper(DocumentStream<S> source,Function<S,D> map) {
            this.source=source;
            this.map=map;
        }
        @Override
        public boolean hasNext() {
            return source.hasNext();
        }
        @Override
        public void close() {
            source.close();
        }
        @Override
        public D next() {
            D d=map.apply(source.next());
            for(Consumer<D> c:listeners)
                c.accept(d);
            return d;
        }
        @Override
        public boolean canRewind() {
            return source.canRewind();
        }
        @Override
        public DocumentStream<D> rewind() {
            return new DocumentStreamMapper<S,D>(source.rewind(),map);
        }
        @Override
        public void tee(Consumer<D> t) {
            listeners.add(t);
        }
    }

}
