// PROBLEM: none
// K2_ERROR: 'operator' modifier is not applicable to function: must have a single value parameter.
// ERROR: 'operator' modifier is inapplicable on this function: must have a single value parameter
fun test() {
    class Test{
        operator fun plus(a: Int=1, b: Int=2) : Int = 0
    }
    val test = Test()
    test.p<caret>lus(b=3)
}
