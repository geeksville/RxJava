/**
 * Copyright 2014 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rx.subjects;

import java.util.ArrayList;
import java.util.List;

import rx.Observer;
import rx.exceptions.CompositeException;
import rx.exceptions.Exceptions;
import rx.functions.Action1;
import rx.internal.operators.NotificationLite;
import rx.subjects.SubjectSubscriptionManager.SubjectObserver;

/**
 * Subject that, once an {@link Observer} has subscribed, emits all subsequently observed items to the
 * subscriber.
 * <p>
 * <img width="640" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/S.PublishSubject.png" alt="">
 * <p>
 * Example usage:
 * <p>
 * <pre> {@code

  PublishSubject<Object> subject = PublishSubject.create();
  // observer1 will receive all onNext and onCompleted events
  subject.subscribe(observer1);
  subject.onNext("one");
  subject.onNext("two");
  // observer2 will only receive "three" and onCompleted
  subject.subscribe(observer2);
  subject.onNext("three");
  subject.onCompleted();

  } </pre>
 * 
 * @param <T>
 *          the type of items observed and emitted by the Subject
 */
public final class PublishSubject<T> extends Subject<T, T> {

    /**
     * Creates and returns a new {@code PublishSubject}.
     *
     * @param <T> the value type
     * @return the new {@code PublishSubject}
     */
    public static <T> PublishSubject<T> create() {
        final SubjectSubscriptionManager<T> state = new SubjectSubscriptionManager<T>();
        state.onTerminated = new Action1<SubjectObserver<T>>() {

            @Override
            public void call(SubjectObserver<T> o) {
                o.emitFirst(state.get(), state.nl);
            }
            
        };
        return new PublishSubject<T>(state, state);
    }

    final SubjectSubscriptionManager<T> state;
    private final NotificationLite<T> nl = NotificationLite.instance();
    
    protected PublishSubject(OnSubscribe<T> onSubscribe, SubjectSubscriptionManager<T> state) {
        super(onSubscribe);
        this.state = state;
    }

    @Override
    public void onCompleted() {
        if (state.active) {
            Object n = nl.completed();
            for (SubjectObserver<T> bo : state.terminate(n)) {
                bo.emitNext(n, state.nl);
            }
        }

    }

    @Override
    public void onError(final Throwable e) {
        if (state.active) {
            Object n = nl.error(e);
            List<Throwable> errors = null;
            for (SubjectObserver<T> bo : state.terminate(n)) {
                try {
                    bo.emitNext(n, state.nl);
                } catch (Throwable e2) {
                    if (errors == null) {
                        errors = new ArrayList<Throwable>();
                    }
                    errors.add(e2);
                }
            }
            if (errors != null) {
                if (errors.size() == 1) {
                    Exceptions.propagate(errors.get(0));
                } else {
                    throw new CompositeException("Errors while emitting PublishSubject.onError", errors);
                }
            }
        }
    }

    @Override
    public void onNext(T v) {
        for (SubjectObserver<T> bo : state.observers()) {
            bo.onNext(v);
        }
    }

    @Override
    public boolean hasObservers() {
        return state.observers().length > 0;
    }
}
