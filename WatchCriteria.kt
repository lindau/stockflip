sealed class WatchCriteria {
    data class PriceTargetCriteria(
        val threshold: Double,
        val comparison: ComparisonType
    ) : WatchCriteria()
    
    data class PERatioCriteria(
        val threshold: Double,
        val comparison: ComparisonType
    ) : WatchCriteria()
    
    data class PSRatioCriteria(
        val threshold: Double,
        val comparison: ComparisonType
    ) : WatchCriteria()
    
    data class ATHDropCriteria(
        val dropPercentage: Double
    ) : WatchCriteria()
    
    data class DailyHighDropCriteria(
        val dropPercentage: Double
    ) : WatchCriteria()
}

enum class ComparisonType {
    ABOVE,
    BELOW
}


