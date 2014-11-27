/*
 * Copyright (c) 2011-2014 Pivotal Software, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package reactor.rx.action;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactor.core.Environment;
import reactor.event.dispatch.Dispatcher;
import reactor.rx.subscription.PushSubscription;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * @author Stephane Maldini
 * @since 2.0
 */
public class DynamicMergeAction<I, O> extends Action<Publisher<? extends I>, O> {

	private final FanInAction<I, ?, O, ? extends FanInAction.InnerSubscriber<I, ?, O>> fanInAction;

	private volatile int finish = 0;

	protected static final AtomicIntegerFieldUpdater<DynamicMergeAction> TERMINAL_UPDATER = AtomicIntegerFieldUpdater
			.newUpdater(DynamicMergeAction.class, "finish");


	@SuppressWarnings("unchecked")
	public DynamicMergeAction(
			Dispatcher dispatcher,
			FanInAction<I, ?, O, ? extends FanInAction.InnerSubscriber<I, ?, O>> fanInAction
	) {
		super(dispatcher);
		this.fanInAction = fanInAction == null ?
				(FanInAction<I, ?, O, ? extends FanInAction.InnerSubscriber<I, ?, O>>) new MergeAction<O>
						(dispatcher) :
				fanInAction;

		this.fanInAction.dynamicMergeAction = this;
	}

	@Override
	public void subscribe(Subscriber<? super O> subscriber) {
		fanInAction.subscribe(subscriber);
	}

	@Override
	protected PushSubscription<O> createSubscription(Subscriber<? super O> subscriber, boolean reactivePull) {
		throw new IllegalAccessError("Should never use dynamicMergeAction own createSubscription");
	}

	@Override
	public long resetChildRequests() {
		return fanInAction.resetChildRequests();
	}

	@Override
	public void replayChildRequests(long request) {
		fanInAction.replayChildRequests(request);
	}

	@Override
	protected void doNext(Publisher<? extends I> value) {
		fanInAction.addPublisher(value);
	}

	@Override
	protected void doComplete() {
		if(TERMINAL_UPDATER.compareAndSet(this, 0, 1)) {
			cancel();
			fanInAction.scheduleCompletion();
		}
	}

	@Override
	protected void doError(Throwable ev) {
		if(TERMINAL_UPDATER.compareAndSet(this, 0, 1)) {
			cancel();
			fanInAction.doError(ev);
		}
	}

	@Override
	public Action<Publisher<? extends I>, O> capacity(long elements) {
		fanInAction.capacity(elements);
		return super.capacity(elements);
	}

	@Override
	public Action<Publisher<? extends I>, O> env(Environment environment) {
		fanInAction.env(environment);
		return super.env(environment);
	}


	public FanInAction<I, ?, O, ? extends FanInAction.InnerSubscriber<I, ?, O>> mergedStream() {
		return fanInAction;
	}

	boolean hasNoMorePublishers(){
		return finish == 1;
	}

}
