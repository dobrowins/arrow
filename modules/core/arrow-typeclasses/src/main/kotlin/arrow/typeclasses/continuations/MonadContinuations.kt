package arrow.typeclasses.continuations

import arrow.Kind
import arrow.core.Either
import arrow.typeclasses.Awaitable
import arrow.typeclasses.Monad
import arrow.typeclasses.stackLabels
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.experimental.intrinsics.suspendCoroutineOrReturn

open class MonadBlockingContinuation<F, A>(M: Monad<F>, private val latch: Awaitable<Kind<F, A>>, override val context: CoroutineContext) :
        Continuation<Kind<F, A>>, Monad<F> by M, Awaitable<Kind<F, A>> by latch, BindingContinuation<F> {

    override fun resume(value: Kind<F, A>) {
        returnedMonad = value
    }

    override fun resumeWithException(exception: Throwable) {
        resolve(Either.left(exception))
    }

    protected lateinit var returnedMonad: Kind<F, A>

    open fun returnedMonad(): Kind<F, A> =
            awaitBlocking().fold({ throw it }, { result -> flatMap(returnedMonad, { result }) })

    override suspend fun <B> bind(m: () -> Kind<F, B>): B = suspendCoroutineOrReturn { c ->
        val labelHere = c.stackLabels // save the whole coroutine stack labels
        returnedMonad = flatMap(m(), { x: B ->
            c.stackLabels = labelHere
            c.resume(x)
            returnedMonad
        })
        COROUTINE_SUSPENDED
    }
}