/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail;

import java.util.List;

record InvestigationSession(String name, String createdAt, List<String> files) { }
