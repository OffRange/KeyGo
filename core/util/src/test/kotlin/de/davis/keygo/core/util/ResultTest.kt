package de.davis.keygo.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResultTest {

    @Test
    fun `onSuccess executes action and provides smart-cast value`() {
        val result: Result<Int, String> = Result.Success(42)
        var captured = 0
        result.onSuccess { captured = it }
        assertEquals(42, captured)
    }

    @Test
    fun `onSuccess does not execute action for Failure`() {
        var executed = false
        Result.Failure<Int, String>("error").onSuccess { executed = true }
        assertFalse(executed)
    }

    @Test
    fun `onSuccess returns the same result for chaining`() {
        val result: Result<Int, String> = Result.Success(42)
        val returned = result.onSuccess { }
        assertEquals(result, returned)
    }

    @Test
    fun `onFailure executes action and provides smart-cast error`() {
        val result: Result<Int, String> = Result.Failure("error")
        var captured = ""
        result.onFailure { captured = it }
        assertEquals("error", captured)
    }

    @Test
    fun `onFailure does not execute action for Success`() {
        var executed = false
        Result.Success<Int, String>(42).onFailure { executed = true }
        assertFalse(executed)
    }

    @Test
    fun `onFailure returns the same result for chaining`() {
        val result: Result<Int, String> = Result.Failure("error")
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
        val result = Result.Failure<Int, String>("error").mapSuccess { it * 2 }
        assertTrue(result.isFailure())
        assertEquals("error", result.error)
    }

    @Test
    fun `mapFailure transforms error value`() {
        val result = Result.Failure<Int, String>("error").mapFailure { it.length }
        assertTrue(result.isFailure())
        assertEquals(5, result.error)
    }

    @Test
    fun `mapFailure preserves success`() {
        val result = Result.Success<Int, String>(42).mapFailure { it.length }
        assertTrue(result.isSuccess())
        assertEquals(42, result.success)
    }

    @Test
    fun `getOrNull returns value for Success`() {
        assertEquals(42, Result.Success<Int, String>(42).getOrNull())
    }

    @Test
    fun `getOrNull returns null for Failure`() {
        assertNull(Result.Failure<Int, String>("error").getOrNull())
    }

    @Test
    fun `asUnitResult maps success to Unit`() {
        val result = Result.Success<Int, String>(42).asUnitResult()
        assertTrue(result.isSuccess())
        assertEquals(Unit, result.success)
    }

    @Test
    fun `asUnitResult preserves failure`() {
        val result = Result.Failure<Int, String>("error").asUnitResult()
        assertTrue(result.isFailure())
        assertEquals("error", result.error)
    }

    @Test
    fun `Boolean asResult returns Success for true`() {
        assertTrue(true.asResult("error").isSuccess())
    }

    @Test
    fun `Boolean asResult returns Failure for false`() {
        val result = false.asResult("error")
        assertTrue(result.isFailure())
        assertEquals("error", result.error)
    }

    @Test
    fun `nullable asResult returns Success for non-null`() {
        val result = "hello".asResult("error")
        assertTrue(result.isSuccess())
        assertEquals("hello", result.success)
    }

    @Test
    fun `nullable asResult returns Failure for null`() {
        val value: String? = null
        val result = value.asResult("error")
        assertTrue(result.isFailure())
        assertEquals("error", result.error)
    }

    @Test
    fun `zip chains two successful results`() {
        val result = Result.Success<Int, String>(1)
            .zip { Result.Success<String, String>("two") }

        assertTrue(result.isSuccess())
        assertEquals(1, result.success.success1)
        assertEquals("two", result.success.success2)
    }

    @Test
    fun `zip short-circuits on first failure`() {
        var secondCalled = false
        val result = Result.Failure<Int, String>("first")
            .zip {
                secondCalled = true
                Result.Success<String, String>("two")
            }

        assertTrue(result.isFailure())
        assertEquals("first", result.error)
        assertFalse(secondCalled)
    }

    @Test
    fun `zip returns second failure when first succeeds`() {
        val result = Result.Success<Int, String>(1)
            .zip { Result.Failure<String, String>("second") }

        assertTrue(result.isFailure())
        assertEquals("second", result.error)
    }

    @Test
    fun `three-way zip chains all successes`() {
        val result = Result.Success<Int, String>(1)
            .zip { Result.Success<String, String>("two") }
            .zip { _, _ -> Result.Success<Double, String>(3.0) }

        assertTrue(result.isSuccess())
        assertEquals(1, result.success.success1)
        assertEquals("two", result.success.success2)
        assertEquals(3.0, result.success.success3)
    }

    @Test
    fun `four-way zip chains all successes`() {
        val result = Result.Success<Int, String>(1)
            .zip { Result.Success<String, String>("two") }
            .zip { _, _ -> Result.Success<Double, String>(3.0) }
            .zip { _, _, _ -> Result.Success<Boolean, String>(true) }

        assertTrue(result.isSuccess())
        assertEquals(1, result.success.success1)
        assertEquals("two", result.success.success2)
        assertEquals(3.0, result.success.success3)
        assertEquals(true, result.success.success4)
    }

    @Test
    fun `three-way zip short-circuits on middle failure`() {
        var thirdCalled = false
        val result = Result.Success<Int, String>(1)
            .zip { Result.Failure<String, String>("middle") }
            .zip { _, _ ->
                thirdCalled = true
                Result.Success<Double, String>(3.0)
            }

        assertTrue(result.isFailure())
        assertEquals("middle", result.error)
        assertFalse(thirdCalled)
    }

    @Test
    fun `chaining onSuccess and onFailure`() {
        var successValue = 0
        var failureValue = ""

        Result.Success<Int, String>(42)
            .onSuccess { successValue = it }
            .onFailure { failureValue = it }

        assertEquals(42, successValue)
        assertEquals("", failureValue)
    }
}
