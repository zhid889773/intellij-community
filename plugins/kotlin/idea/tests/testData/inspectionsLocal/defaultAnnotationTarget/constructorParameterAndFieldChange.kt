// CHOSEN_OPTION: Add use-site target 'field'
// LANGUAGE_VERSION: 2.0

@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
internal annotation class Anno

class MyClass(<caret>@Anno val foo: String)
