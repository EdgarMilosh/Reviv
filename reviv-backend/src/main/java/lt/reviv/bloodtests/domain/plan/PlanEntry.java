package lt.reviv.bloodtests.domain.plan;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "consumption_plan_entry")
public class PlanEntry {
    public enum DayPart { MORNING, NOON, EVENING }
    public enum MealRelation { BEFORE_MEAL, WITH_MEAL, AFTER_MEAL, INDEPENDENT, UNKNOWN }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "plan_id", nullable = false)
    private Long planId;
    @Column(name = "recommendation_id", nullable = false)
    private Long recommendationId;
    @Column(name = "supplement_id", nullable = false)
    private Long supplementId;
    @Column(name = "supplement_name_snapshot", nullable = false, length = 120)
    private String supplementNameSnapshot;
    @Column(name = "daily_dose", nullable = false, precision = 12, scale = 4)
    private BigDecimal dailyDose;
    @Column(name = "dose_unit", nullable = false, length = 20)
    private String doseUnit;
    @Enumerated(EnumType.STRING)
    @Column(name = "day_part", nullable = false, length = 20)
    private DayPart dayPart;
    @Enumerated(EnumType.STRING)
    @Column(name = "meal_relation", nullable = false, length = 20)
    private MealRelation mealRelation;
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
    @Column(name = "break_days")
    private Integer breakDays;
    @Column(name = "notes", length = 800)
    private String notes;

    protected PlanEntry() {}

    public PlanEntry(Long planId, Long recommendationId, Long supplementId, String name,
                     BigDecimal dailyDose, String doseUnit, DayPart dayPart, MealRelation mealRelation,
                     LocalDate startDate, LocalDate endDate, Integer breakDays, String notes) {
        this.planId = planId;
        this.recommendationId = recommendationId;
        this.supplementId = supplementId;
        this.supplementNameSnapshot = name;
        update(dailyDose, dayPart, mealRelation, startDate, endDate, breakDays, notes);
        this.doseUnit = doseUnit;
    }

    public void update(BigDecimal dailyDose, DayPart dayPart, MealRelation mealRelation,
                       LocalDate startDate, LocalDate endDate, Integer breakDays, String notes) {
        this.dailyDose = dailyDose;
        this.dayPart = dayPart;
        this.mealRelation = mealRelation;
        this.startDate = startDate;
        this.endDate = endDate;
        this.breakDays = breakDays;
        this.notes = notes;
    }

    public Long getId() { return id; }
    public Long getPlanId() { return planId; }
    public Long getRecommendationId() { return recommendationId; }
    public Long getSupplementId() { return supplementId; }
    public String getSupplementNameSnapshot() { return supplementNameSnapshot; }
    public BigDecimal getDailyDose() { return dailyDose; }
    public String getDoseUnit() { return doseUnit; }
    public DayPart getDayPart() { return dayPart; }
    public MealRelation getMealRelation() { return mealRelation; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public Integer getBreakDays() { return breakDays; }
    public String getNotes() { return notes; }
}
