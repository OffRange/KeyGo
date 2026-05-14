package de.davis.keygo.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResultTest {

    sealed interface TestError {
        data object ErrorA : TestError
        data object ErrorB : TestError
    }

    data class User(val name: String)
    data class Profile(val user: User)

    private fun getUser(): Result<User, TestError.ErrorA> {
        return Result.Success(User("John"))
    }

    private fun getUserFailure(): Result<User, TestError.ErrorA> {
        return Result.Failure(TestError.ErrorA)
    }

    private fun getProfile(user: User): Result<Profile, TestError.ErrorB> {
        return Result.Success(Profile(user))
    }

    private fun getProfileFailure(): Result<Profile, TestError.ErrorB> {
        return Result.Failure(TestError.ErrorB)
    }

    @Test
    fun `onSuccess executes action and provides smart-cast value`() {
        val result: Result<Int, TestError> = Result.Success(42)

        var captured = 0

        result.onSuccess { captured = it }

        assertEquals(42, captured)
    }

    @Test
    fun `onSuccess does not execute action for Failure`() {
        var executed = false

        Result.Failure<Unit, TestError>(TestError.ErrorA)
            .onSuccess { executed = true }

        assertFalse(executed)
    }

    @Test
    fun `onSuccess returns the same result for chaining`() {
        val result: Result<Int, TestError> = Result.Success(42)

        val returned = result.onSuccess { }

        assertEquals(result, returned)
    }

    @Test
    fun `onFailure executes action and provides smart-cast error`() {
        val result: Result<Int, TestError> = Result.Failure(TestError.ErrorA)
        var captured: TestError? = null
        result.onFailure { captured = it }
        assertEquals(TestError.ErrorA, captured)
    }

    @Test
    fun `onFailure does not execute action for Success`() {
        var executed = false
        Result.Success<Int, String>(42).onFailure { executed = true }
        assertFalse(executed)
    }

    @Test
    fun `onFailure returns the same result for chaining`() {
        val result: Result<Int, TestError> = Result.Failure(TestError.ErrorA)
        val returned = result.onFailure { }
        assertEquals(result, returned)
    }

    @Test
    fun `mapSuccess transforms success value`() {
        val result = Result.Success<Int, String>(42).mapSuccess { it * 2 }
        assertTrue(result.isSuccess())
        assertEquals(84, result.success)
    }

    @Test
    fun `mapSuccess preserves failure`() {
        val result = Result.Failure<Int, TestError>(TestError.ErrorA).mapSuccess { it * 2 }
        assertTrue(result.isFailure())
        assertEquals(TestError.ErrorA, result.error)
    }

    @Test
    fun `mapFailure transforms error value`() {
        val result = Result.Failure<Int, TestError>(TestError.ErrorA).mapFailure {
            when (it) {
                TestError.ErrorA -> "mapped-a"
                TestError.ErrorB -> "mapped-b"
            }
        }

        assertTrue(result.isFailure())
        assertEquals("mapped-a", result.error)
    }

    @Test
    fun `mapFailure preserves success`() {
        val result = Result.Success<Int, TestError>(42)
            .mapFailure {
                when (it) {
                    TestError.ErrorA -> "mapped-a"
                    TestError.ErrorB -> "mapped-b"
                }
            }

        assertTrue(result.isSuccess())
        assertEquals(42, result.success)
    }

    @Test
    fun `getOrNull returns value for Success`() {
        assertEquals(42, Result.Success<Int, String>(42).getOrNull())
    }

    @Test
    fun `getOrNull returns null for Failure`() {
        assertNull(Result.Failure<Int, TestError>(TestError.ErrorA).getOrNull())
    }

    @Test
    fun `asUnitResult maps success to Unit`() {
        val result = Result.Success<Int, String>(42).asUnitResult()
        assertTrue(result.isSuccess())
        assertEquals(Unit, result.success)
    }

    @Test
    fun `asUnitResult preserves failure`() {
        val result = Result.Failure<Int, TestError>(TestError.ErrorA).asUnitResult()

        assertTrue(result.isFailure())
        assertEquals(TestError.ErrorA, result.error)
    }

    @Test
    fun `Boolean asResult returns Success for true`() {
        assertTrue(
            true.asResult(TestError.ErrorA)
                .isSuccess()
        )
    }

    @Test
    fun `Boolean asResult returns Failure for false`() {
        val result = false.asResult(TestError.ErrorA)

        assertTrue(result.isFailure())
        assertEquals(TestError.ErrorA, result.error)
    }

    @Test
    fun `nullable asResult returns Success for non-null`() {
        val result = "hello".asResult(TestError.ErrorA)

        assertTrue(result.isSuccess())
        assertEquals("hello", result.success)
    }

    @Test
    fun `nullable asResult returns Failure for null`() {
        val value: String? = null

        val result = value.asResult(TestError.ErrorA)

        assertTrue(result.isFailure())
        assertEquals(TestError.ErrorA, result.error)
    }

    @Test
    fun `chaining onSuccess and onFailure`() {
        var successValue = 0
        var failureValue: TestError? = null

        Result.Success<Int, TestError>(42)
            .onSuccess { successValue = it }
            .onFailure { failureValue = it }

        assertEquals(42, successValue)
        assertNull(failureValue)
    }

    @Test
    fun `bind returns success value`() {
        val result = resultBinding<String, TestError> {
            val user = getUser().bind()
            user.name
        }

        assertIs<Result.Success<String, TestError>>(result)
        assertEquals("John", result.success)
    }

    @Test
    fun `bind short circuits on first failure`() {
        val result = resultBinding<String, TestError> {
            val user = getUserFailure().bind()
            user.name
        }

        assertIs<Result.Failure<String, TestError>>(result)
        assertEquals(TestError.ErrorA, result.error)
    }

    @Test
    fun `bind propagates subtype errors`() {
        val result = resultBinding {
            assertIs<User>(getUser().bind())
            getProfileFailure().bind()
        }

        assertIs<Result.Failure<String, TestError>>(result)
        assertEquals(TestError.ErrorB, result.error)
    }

    @Test
    fun `multiple bind calls return final success`() {
        val result = resultBinding {
            val user = getUser().bind()
            getProfile(user).bind()
        }

        assertIs<Result.Success<Profile, TestError>>(result)
        assertEquals("John", result.success.user.name)
    }

    @Test
    fun `execution stops after failed bind`() {
        var executed = false

        val result = resultBinding<Unit, TestError> {
            getUserFailure().bind()

            executed = true
        }

        assertIs<Result.Failure<Unit, TestError>>(result)
        assertEquals(false, executed)
    }

    @Test
    fun `successful binds continue execution`() {
        var executed = false

        val result = resultBinding<Unit, TestError> {
            getUser().bind()

            executed = true
        }

        assertIs<Result.Success<Unit, TestError>>(result)
        assertEquals(true, executed)
    }

    @Test
    fun `fold executes onSuccess for success result`() {
        val result: Result<String, TestError> = Result.Success("hello")

        val value = result.fold(
            onSuccess = { it.uppercase() },
            onFailure = { "error" }
        )

        assertEquals("HELLO", value)
    }

    @Test
    fun `fold executes onFailure for failure result`() {
        val result: Result<String, TestError> = Result.Failure(TestError.ErrorA)

        val value = result.fold(
            onSuccess = { it.uppercase() },
            onFailure = { "error" }
        )

        assertEquals("error", value)
    }
}
