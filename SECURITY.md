# Security policy

## Reporting a vulnerability

Please use GitHub's private vulnerability reporting feature for issues that could expose log contents or bypass redaction. Do not include real credentials or production logs in reports.

## Security model

TraceTail reads only files explicitly opened by the user. It performs no network requests and stores no application data outside process memory. Redaction is defense in depth, not a substitute for controlling access to sensitive log files.

The portable distribution and runtime archive are built from the source and dependency versions recorded in the repository. Release artifacts are published through the repository's GitHub Releases page.
