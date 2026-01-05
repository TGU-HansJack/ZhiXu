package app.zhixu.pomodoro

enum class PomodoroMode {
    Focus,
    ShortBreak,
    LongBreak,
    ;

    internal val wire: String
        get() =
            when (this) {
                Focus -> "focus"
                ShortBreak -> "short_break"
                LongBreak -> "long_break"
            }

    companion object {
        internal fun fromWire(value: String): PomodoroMode? =
            when (value.trim().lowercase()) {
                "focus" -> Focus
                "short_break", "shortbreak" -> ShortBreak
                "long_break", "longbreak" -> LongBreak
                else -> null
            }
    }
}
