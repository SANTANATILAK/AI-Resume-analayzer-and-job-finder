
## Starter companies
On first start (empty companies table) the app loads src/main/resources/seed/companies.csv: the S&P 500 constituents from the public-domain dataset github.com/datasets/s-and-p-500-companies (ODC-PDDL), minus consultancy/staffing firms, plus about 40 large Indian companies added by hand. Websites and careers links are not in the file; add them in Admin. Review the list before relying on it. These are companies, not vacancies.

## Sample data and local run (added)
- Default database is now an embedded H2 file (./data/internmatch), so `mvn spring-boot:run` works with no MySQL. For MySQL set DB_URL, DB_USERNAME and DB_PASSWORD as before.
- companies.csv now has 687 companies (S&P 500 minus consultancies, plus about 190 Indian companies: IT, product, banking, pharma, core engineering, startups).
- jobs.csv has 1,501 SAMPLE roles (2-3 per company) built from typical entry-level requirements per industry. The UI labels them "Sample data". They are not confirmed vacancies, salaries are indicative, and Apply opens a careers search because no verified apply link exists. Replace them with real roles via Admin or a SERPAPI_KEY sync.
- Seeding runs once, when no "Sample data" roles exist yet.
- Resume skill detection also recognises common short forms (js, reactjs, nodejs, dsa, ml, postgres).
- GET /api/companies/matches now takes q, minMatch, industry, page, size and returns {items,total,pages,strong,hasResume}.
