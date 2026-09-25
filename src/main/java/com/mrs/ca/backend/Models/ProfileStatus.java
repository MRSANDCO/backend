package com.mrs.ca.backend.Models;

/**
 * Lifecycle status of an employee's self-service profile form.
 * <ul>
 *   <li>INCOMPLETE – employee has not yet submitted their profile form.</li>
 *   <li>SUBMITTED  – employee has filled and submitted the profile (locked for edits).</li>
 *   <li>VERIFIED   – admin has verified the submitted profile details.</li>
 *   <li>REJECTED   – admin has rejected the submission; employee may need to resubmit.</li>
 * </ul>
 */
public enum ProfileStatus {
    INCOMPLETE,
    SUBMITTED,
    VERIFIED,
    REJECTED
}
