package edu.cit.berou.supplier;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Switches on @Scheduled for the retry and tracking jobs in this module. */
@Configuration
@EnableScheduling
class SupplierConfig {
}
