package lt.reviv.bloodtests.domain.plan;

import jakarta.persistence.*;
import java.time.LocalTime;

@Entity
@Table(name = "consumption_plan_reminder")
public class PlanReminder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "plan_entry_id", nullable = false, unique = true)
    private Long planEntryId;
    @Column(name = "reminder_time", nullable = false)
    private LocalTime reminderTime;
    @Column(nullable = false)
    private boolean enabled;

    protected PlanReminder() {}
    public PlanReminder(Long planEntryId, LocalTime reminderTime) {
        this.planEntryId = planEntryId;
        this.reminderTime = reminderTime;
        this.enabled = true;
    }
    public void reschedule(LocalTime reminderTime) { this.reminderTime = reminderTime; }
    public Long getId() { return id; }
    public Long getPlanEntryId() { return planEntryId; }
    public LocalTime getReminderTime() { return reminderTime; }
    public boolean isEnabled() { return enabled; }
}
