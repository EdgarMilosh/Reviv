package lt.reviv.supplements.service;

/**
 * Atsako, ar papildas naudojamas vartojimo planuose (DVP4 – Vartojimo plano duomenų komponentas).
 * US-11 metu realizacija turi būti pakeista tikra užklausa į vartojimo plano lenteles.
 */
public interface SupplementUsage {

    boolean isUsedInPlans(Long supplementId);
}