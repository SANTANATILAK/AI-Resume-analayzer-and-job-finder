package com.nani.internship;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*; import jakarta.validation.Valid; import jakarta.validation.constraints.*;
import java.security.MessageDigest; import java.time.*; import java.util.*;
import org.springframework.beans.factory.annotation.Value; import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.scheduling.annotation.Scheduled; import org.springframework.security.core.Authentication;
import org.springframework.stereotype.*; import org.springframework.web.bind.annotation.*; import org.springframework.web.client.RestClient;

@Entity @Table(name="saved_opportunities",uniqueConstraints=@UniqueConstraint(columnNames={"userId","oppId"})) class Saved {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; Long userId, oppId; }
@Entity @Table(name="applications",uniqueConstraints=@UniqueConstraint(columnNames={"userId","oppId"})) class TrackedApp {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; Long userId, oppId; String status="INTERESTED";
  @Column(columnDefinition="TEXT") String notes; LocalDateTime updatedAt=LocalDateTime.now(); }
@Entity @Table(name="sync_runs") class SyncRun {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; String source, status; LocalDateTime startedAt, finishedAt; int newCount, updatedCount;
  @Column(columnDefinition="TEXT") String error; }
interface SavedRepo extends JpaRepository<Saved,Long>{ List<Saved> findByUserId(Long u); Optional<Saved> findByUserIdAndOppId(Long u,Long o); }
interface TrackRepo extends JpaRepository<TrackedApp,Long>{ List<TrackedApp> findByUserId(Long u); Optional<TrackedApp> findByUserIdAndOppId(Long u,Long o); }
interface SyncRepo extends JpaRepository<SyncRun,Long>{ List<SyncRun> findTop10ByOrderByStartedAtDesc(); Optional<SyncRun> findFirstByStatusOrderByFinishedAtDesc(String s); }

record ProfileReq(@NotNull @Min(2020) @Max(2030) Integer gradYear,@NotBlank String branch){}
record TrackReq(@NotNull Long oppId,String status,String notes){}

/** One adapter per legitimate source. Add more by implementing this interface as a @Component. */
interface SourceAdapter { String name(); boolean enabled(); List<OppReq> fetch() throws Exception; }

@Component class SerpApiAdapter implements SourceAdapter {
  @Value("${app.serpapi-key:}") String key;
  public String name(){ return "SerpApi Google Jobs"; }
  public boolean enabled(){ return key!=null&&!key.isBlank(); }
  public List<OppReq> fetch() throws Exception {
    List<OppReq> out=new ArrayList<>();
    for(String q:List.of("internship India","software engineer fresher India")){
      JsonNode n=RestClient.create().get().uri("https://serpapi.com/search.json?engine=google_jobs&q={q}&api_key={k}",q,key).retrieve().body(JsonNode.class);
      for(JsonNode j:n.path("jobs_results")){
        String link=j.path("apply_options").path(0).path("link").asText(""), title=j.path("title").asText(""), desc=j.path("description").asText("");
        if(link.isBlank()||title.isBlank()) continue;
        String id=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(j.path("job_id").asText(link).getBytes()));
        String low=desc.toLowerCase(); List<String> sk=new ArrayList<>();
        for(String s:ResumeController.SKILLS) if(java.util.regex.Pattern.compile("(?<![a-z0-9])"+java.util.regex.Pattern.quote(s)+"(?![a-z0-9])").matcher(low).find()) sk.add(s);
        // verified=false on purpose: the link comes from an aggregator, so an admin must confirm it is the employer's page.
        out.add(new OppReq("serpapi:"+id,j.path("company_name").asText("Unknown"),title,title.toLowerCase().contains("intern")?"INTERNSHIP":"FULL_TIME",desc,String.join(",",sk),
          null,null,j.path("location").asText(null),null,null,null,link,name(),false,"ACTIVE",null)); } }
    return out; }
}

@Service class SyncService {
  final List<SourceAdapter> adapters; final OppRepo opps; final SyncRepo runs;
  SyncService(List<SourceAdapter> a,OppRepo o,SyncRepo r){adapters=a;opps=o;runs=r;}
  @Scheduled(cron="0 0 * * * *") void hourly(){ runAll(); }
  synchronized List<SyncRun> runAll(){
    List<SyncRun> out=new ArrayList<>();
    for(SourceAdapter a:adapters){
      SyncRun r=new SyncRun(); r.source=a.name(); r.startedAt=LocalDateTime.now();
      if(!a.enabled()){ r.status="NOT_CONFIGURED"; r.error="API credentials are not set for this source."; }
      else { List<OppReq> list=null; Exception last=null;
        for(int i=0;i<3&&list==null;i++){ try{ list=a.fetch(); }catch(Exception e){ last=e; try{ Thread.sleep(2000L*(i+1)); }catch(InterruptedException x){ Thread.currentThread().interrupt(); break; } } }
        if(list==null){ r.status="FAILED"; r.error=String.valueOf(last); }
        else { for(OppReq q:list){ Optional<Opportunity> ex=opps.findBySourceId(q.sourceId()); Opportunity o=ex.orElseGet(Opportunity::new);
            if(ex.isEmpty()){ o.sourceId=q.sourceId(); o.company=q.company(); o.category=q.category(); o.sourceName=q.sourceName(); o.verified=false; o.gradYears=q.gradYears(); r.newCount++; } else r.updatedCount++;
            if(ex.isPresent()&&!Objects.equals(o.applyUrl,q.applyUrl())) o.verified=false;
            o.title=q.title(); o.description=q.description(); o.requiredSkills=q.requiredSkills(); o.location=q.location(); o.applyUrl=q.applyUrl(); o.status="ACTIVE"; o.updatedAt=LocalDateTime.now(); opps.save(o); }
          r.status="SUCCESS";
          if(!list.isEmpty()){ Set<String> seen=new HashSet<>(); for(OppReq q:list) seen.add(q.sourceId());
            for(Opportunity o:opps.findAll()) if(a.name().equals(o.sourceName)&&"ACTIVE".equals(o.status)&&!seen.contains(o.sourceId)){ o.status="CLOSED"; opps.save(o); } } } }
      r.finishedAt=LocalDateTime.now(); runs.save(r); out.add(r); }
    LocalDate today=LocalDate.now();
    for(Opportunity o:opps.findAll()) if("ACTIVE".equals(o.status)&&o.closingDate!=null&&o.closingDate.isBefore(today)){ o.status="EXPIRED"; opps.save(o); }
    return out; }
}

@RestController @RequestMapping("/api") class UserDataController {
  static final Set<String> STATUSES=Set.of("INTERESTED","APPLIED","INTERVIEWING","OFFER","REJECTED");
  final UserRepo users; final SavedRepo saved; final TrackRepo track; final OppRepo opps; final SyncRepo runs; final SyncService sync;
  UserDataController(UserRepo u,SavedRepo s,TrackRepo t,OppRepo o,SyncRepo r,SyncService y){users=u;saved=s;track=t;opps=o;runs=r;sync=y;}
  User me(Authentication a){ return users.findByEmail(a.getName()).orElseThrow(); }
  @GetMapping("/me") Map<String,Object> profile(Authentication a){ User u=me(a); return Map.of("fullName",u.fullName,"email",u.email,"gradYear",u.gradYear,"branch",u.branch,"role",u.role); }
  @PutMapping("/me") Map<String,Object> update(Authentication a,@Valid @RequestBody ProfileReq r){ User u=me(a); u.gradYear=r.gradYear(); u.branch=r.branch(); users.save(u); return profile(a); }
  @PostMapping("/saved/{id}") void save(Authentication a,@PathVariable Long id){ User u=me(a);
    if(saved.findByUserIdAndOppId(u.id,id).isEmpty()){ Saved s=new Saved(); s.userId=u.id; s.oppId=id; saved.save(s); } }
  @DeleteMapping("/saved/{id}") void unsave(Authentication a,@PathVariable Long id){ saved.findByUserIdAndOppId(me(a).id,id).ifPresent(saved::delete); }
  @GetMapping("/applications") List<Map<String,Object>> apps(Authentication a){
    return track.findByUserId(me(a).id).stream().map(t->{ Opportunity o=opps.findById(t.oppId).orElse(null); Map<String,Object> m=new LinkedHashMap<>();
      m.put("id",t.id); m.put("oppId",t.oppId); m.put("company",o==null?"(removed)":o.company); m.put("title",o==null?"":o.title); m.put("status",t.status); m.put("notes",t.notes==null?"":t.notes); m.put("updatedAt",t.updatedAt.toString()); return m; }).toList(); }
  @PostMapping("/applications") Map<String,Object> trackIt(Authentication a,@Valid @RequestBody TrackReq r){ User u=me(a);
    TrackedApp t=track.findByUserIdAndOppId(u.id,r.oppId()).orElseGet(TrackedApp::new); t.userId=u.id; t.oppId=r.oppId();
    if(r.status()!=null&&STATUSES.contains(r.status())) t.status=r.status(); if(r.notes()!=null) t.notes=r.notes(); t.updatedAt=LocalDateTime.now(); return Map.of("id",track.save(t).id); }
  @DeleteMapping("/applications/{id}") void untrack(Authentication a,@PathVariable Long id){ track.findById(id).filter(t->t.userId.equals(me(a).id)).ifPresent(track::delete); }

  Map<String,Object> runMap(SyncRun r){ Map<String,Object> m=new LinkedHashMap<>(); m.put("source",r.source); m.put("status",r.status); m.put("newCount",r.newCount); m.put("updatedCount",r.updatedCount);
    m.put("finishedAt",r.finishedAt==null?null:r.finishedAt.toString()); m.put("error",r.error); return m; }
  @GetMapping("/sync/status") Map<String,Object> status(){ Map<String,Object> m=new LinkedHashMap<>();
    m.put("lastSuccess",runs.findFirstByStatusOrderByFinishedAtDesc("SUCCESS").map(r->r.finishedAt.toString()).orElse(null)); m.put("recent",runs.findTop10ByOrderByStartedAtDesc().stream().map(this::runMap).toList()); return m; }
  @PostMapping("/admin/sync") List<Map<String,Object>> runSync(){ return sync.runAll().stream().map(this::runMap).toList(); }
  @GetMapping("/admin/opportunities") List<Map<String,Object>> all(){ return opps.findAll().stream().map(o->{ Map<String,Object> m=new LinkedHashMap<>();
    m.put("id",o.id); m.put("company",o.company); m.put("title",o.title); m.put("source",o.sourceName); m.put("verified",o.verified); m.put("status",o.status); m.put("applyUrl",o.applyUrl); return m; }).toList(); }
  @PutMapping("/admin/opportunities/{id}/verify") Map<String,Object> verify(@PathVariable Long id,@RequestParam boolean verified){
    Opportunity o=opps.findById(id).orElseThrow(); o.verified=verified&&o.applyUrl!=null&&o.applyUrl.startsWith("http"); opps.save(o); return Map.of("id",id,"verified",verified); }
}

@Entity @Table(name="companies") class Company {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @Column(unique=true) String name; String logoUrl, website, careersUrl, country, industry; }
interface CompanyRepo extends JpaRepository<Company,Long>{ Optional<Company> findByNameIgnoreCase(String n);
  org.springframework.data.domain.Page<Company> findByNameContainingIgnoreCase(String q,org.springframework.data.domain.Pageable p); }
record CompanyReq(@NotBlank String name,String logoUrl,String website,String careersUrl,String country,String industry){}

@RestController @RequestMapping("/api") class DetailController {
  final UserRepo users; final ResumeRepo resumes; final OppRepo opps; final CompanyRepo companies; final org.springframework.jdbc.core.JdbcTemplate jdbc;
  DetailController(UserRepo u,ResumeRepo r,OppRepo o,CompanyRepo c,org.springframework.jdbc.core.JdbcTemplate j){users=u;resumes=r;opps=o;companies=c;jdbc=j;}
  @GetMapping("/opportunities/{id}") Map<String,Object> detail(Authentication a,@PathVariable Long id){
    User u=users.findByEmail(a.getName()).orElseThrow();
    Opportunity o=opps.findById(id).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Opportunity not found"));
    Set<String> mine=OpportunityController.set(resumes.findByUserId(u.id).map(r->r.skills).orElse("")), req=OpportunityController.set(o.requiredSkills), years=OpportunityController.set(o.gradYears);
    List<String> matched=req.stream().filter(mine::contains).toList(), missing=req.stream().filter(s->!mine.contains(s)).toList();
    boolean expired=o.closingDate!=null&&o.closingDate.isBefore(LocalDate.now());
    boolean can=o.applyUrl!=null&&o.applyUrl.startsWith("http")&&Boolean.TRUE.equals(o.verified)&&!expired&&"ACTIVE".equals(o.status);
    Map<String,Object> m=new LinkedHashMap<>(); m.put("id",o.id); m.put("company",o.company); m.put("title",o.title); m.put("description",o.description); m.put("category",o.category); m.put("experience",o.experience);
    m.put("location",o.location); m.put("workMode",o.workMode); m.put("stipend",o.stipend); m.put("ppo",o.ppo); m.put("source",o.sourceName); m.put("verified",o.verified); m.put("status",o.status);
    m.put("closingDate",o.closingDate); m.put("branches",new ArrayList<>(OpportunityController.set(o.branches))); m.put("gradYears",new ArrayList<>(years)); m.put("requiredSkills",new ArrayList<>(req));
    m.put("matchedSkills",matched); m.put("missingSkills",missing); m.put("matchPercent",req.isEmpty()?0:matched.size()*100/req.size()); m.put("eligible",(years.isEmpty()||years.contains(String.valueOf(u.gradYear)))&&(OpportunityController.set(o.branches).isEmpty()||OpportunityController.set(o.branches).contains(String.valueOf(u.branch).toLowerCase())));
    m.put("canApply",can); m.put("applyUrl",can?o.applyUrl:null); m.put("lastUpdated",o.updatedAt.toString()); m.put("logoUrl",companies.findByNameIgnoreCase(o.company==null?"":o.company).map(c->c.logoUrl).orElse(null)); return m; }
  /** One row per company: best match among its roles that this user is eligible for (graduation year + branch). Filter with q / minMatch, paged. */
  @GetMapping("/companies/matches") Map<String,Object> companyMatches(Authentication a,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int minMatch,@RequestParam(defaultValue="") String industry,
      @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
    User u=users.findByEmail(a.getName()).orElseThrow();
    Set<String> mine=OpportunityController.set(resumes.findByUserId(u.id).map(r->r.skills).orElse(""));
    Map<String,Company> cmap=new HashMap<>(); for(Company c:companies.findAll()) cmap.put(c.name.trim().toLowerCase(),c);
    Map<String,Map<String,Object>> by=new LinkedHashMap<>(); LocalDate today=LocalDate.now();
    for(Opportunity o:opps.findAll()){
      if(!"ACTIVE".equals(o.status)||o.company==null||(o.closingDate!=null&&o.closingDate.isBefore(today))) continue;
      Set<String> req=OpportunityController.set(o.requiredSkills), years=OpportunityController.set(o.gradYears), br=OpportunityController.set(o.branches);
      boolean elig=(years.isEmpty()||years.contains(String.valueOf(u.gradYear)))&&(br.isEmpty()||br.contains(String.valueOf(u.branch).toLowerCase()));
      List<String> matched=req.stream().filter(mine::contains).toList(), missing=req.stream().filter(s->!mine.contains(s)).toList();
      int pct=req.isEmpty()?0:matched.size()*100/req.size();
      Company co=cmap.get(o.company.trim().toLowerCase());
      Map<String,Object> c=by.computeIfAbsent(o.company.trim().toLowerCase(),k->{ Map<String,Object> m=new LinkedHashMap<>(); m.put("id",co==null?null:co.id); m.put("name",o.company.trim()); m.put("industry",co==null?null:co.industry); m.put("country",co==null?null:co.country);
        m.put("website",co==null?null:co.website); m.put("careersUrl",co==null?null:co.careersUrl); m.put("roles",0); m.put("eligibleRoles",0);
        m.put("bestMatch",-1); m.put("bestRoleId",null); m.put("bestRole",null); m.put("matchedSkills",List.of()); m.put("missingSkills",List.of()); m.put("logoUrl",co==null?null:co.logoUrl); return m; });
      c.put("roles",(Integer)c.get("roles")+1);
      if(elig){ c.put("eligibleRoles",(Integer)c.get("eligibleRoles")+1); if(pct>(Integer)c.get("bestMatch")){ c.put("bestMatch",pct); c.put("bestRoleId",o.id); c.put("bestRole",o.title); c.put("matchedSkills",matched); c.put("missingSkills",missing); } } }
    String ql=q.toLowerCase().trim(), il=industry.toLowerCase().trim();
    List<Map<String,Object>> out=new ArrayList<>(by.values()); out.removeIf(x->(!ql.isEmpty()&&!String.valueOf(x.get("name")).toLowerCase().contains(ql))||(!il.isEmpty()&&!String.valueOf(x.get("industry")).toLowerCase().contains(il))||(Integer)x.get("bestMatch")<minMatch);
    out.sort((x,y)->{ int e=Boolean.compare((Integer)y.get("eligibleRoles")>0,(Integer)x.get("eligibleRoles")>0); if(e!=0) return e; int m=Integer.compare((Integer)y.get("bestMatch"),(Integer)x.get("bestMatch")); return m!=0?m:String.valueOf(x.get("name")).compareToIgnoreCase(String.valueOf(y.get("name"))); });
    int sz=Math.min(Math.max(size,1),100), from=Math.min(page*sz,out.size()), to=Math.min(from+sz,out.size());
    Map<String,Object> res=new LinkedHashMap<>(); res.put("items",out.subList(from,to)); res.put("total",out.size()); res.put("pages",(out.size()+sz-1)/sz);
    res.put("hasResume",!mine.isEmpty()||resumes.findByUserId(u.id).isPresent()); res.put("strong",out.stream().filter(x->(Integer)x.get("bestMatch")>=70).count()); return res; }
  /** General, industry-level guidance only. It is NOT an employer's published requirement and is labelled that way in the UI. */
  static List<String> typical(String ind){ String s=ind==null?"":ind.toLowerCase();
    if(s.contains("information technology")) return List.of("python","java","javascript","sql","git","aws","docker","data structures","rest api","machine learning");
    if(s.contains("health")||s.contains("pharma")) return List.of("research","statistics","lab techniques","clinical research","pharmacology","excel","communication");
    if(s.contains("financ")||s.contains("bank")||s.contains("fintech")) return List.of("excel","sql","financial modeling","accounting","statistics","python","communication");
    if(s.contains("communication")||s.contains("telecom")) return List.of("digital marketing","seo","content writing","python","sql","figma","communication");
    if(s.contains("real estate")) return List.of("revit","autocad","excel","financial modeling","project management");
    if(s.contains("utilities")||s.contains("power")) return List.of("plc","scada","matlab","project management","excel");
    if(s.contains("energy")||s.contains("oil")) return List.of("process design","thermodynamics","aspen plus","matlab","project management","excel");
    if(s.contains("materials")||s.contains("metal")||s.contains("mining")||s.contains("cement")||s.contains("paint")) return List.of("process design","quality control","thermodynamics","six sigma","aspen plus");
    if(s.contains("consumer staples")||s.contains("consumer goods")) return List.of("supply chain","excel","sales","quality control","business analysis","communication");
    if(s.contains("consumer discretionary")||s.contains("e-commerce")||s.contains("food delivery")||s.contains("automobile")||s.contains("electric vehicles")) return List.of("excel","sql","digital marketing","sales","supply chain","business analysis","communication");
    if(s.contains("industrial")||s.contains("engineering")||s.contains("aerospace")||s.contains("defence")||s.contains("construction")) return List.of("autocad","solidworks","matlab","project management","supply chain","six sigma","quality control");
    return List.of("communication","excel","project management","business analysis"); }
  @GetMapping("/companies/{id}") Map<String,Object> companyDetail(Authentication a,@PathVariable Long id){
    User u=users.findByEmail(a.getName()).orElseThrow();
    Company c=companies.findById(id).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Company not found"));
    Set<String> mine=OpportunityController.set(resumes.findByUserId(u.id).map(r->r.skills).orElse("")); LocalDate today=LocalDate.now(); List<Map<String,Object>> roles=new ArrayList<>();
    for(Opportunity o:opps.findByCompanyIgnoreCaseAndStatus(c.name,"ACTIVE")){
      if(o.closingDate!=null&&o.closingDate.isBefore(today)) continue; Set<String> req=OpportunityController.set(o.requiredSkills);
      List<String> matched=req.stream().filter(mine::contains).toList(), missing=req.stream().filter(s->!mine.contains(s)).toList(); Map<String,Object> r=new LinkedHashMap<>();
      r.put("id",o.id); r.put("title",o.title); r.put("category",o.category); r.put("location",o.location); r.put("workMode",o.workMode); r.put("stipend",o.stipend);
      r.put("matchedSkills",matched); r.put("missingSkills",missing); r.put("matchPercent",req.isEmpty()?0:matched.size()*100/req.size()); roles.add(r); }
    roles.sort((x,y)->Integer.compare((Integer)y.get("matchPercent"),(Integer)x.get("matchPercent")));
    List<String> t=typical(c.industry); Map<String,Object> m=new LinkedHashMap<>(cm(c)); m.put("roles",roles); m.put("typicalHave",t.stream().filter(mine::contains).toList()); m.put("typicalMissing",t.stream().filter(s->!mine.contains(s)).toList()); return m; }
  Map<String,Object> cm(Company c){ Map<String,Object> m=new LinkedHashMap<>(); m.put("id",c.id); m.put("name",c.name); m.put("logoUrl",c.logoUrl); m.put("website",c.website); m.put("careersUrl",c.careersUrl); m.put("country",c.country); m.put("industry",c.industry); return m; }
  @GetMapping("/companies") Map<String,Object> companies(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
    var pg=companies.findByNameContainingIgnoreCase(q,org.springframework.data.domain.PageRequest.of(page,Math.min(size,100),org.springframework.data.domain.Sort.by("name")));
    return Map.of("items",pg.getContent().stream().map(this::cm).toList(),"total",pg.getTotalElements(),"pages",pg.getTotalPages()); }
  /** Names containing these words are skipped on import (no consultancy / staffing firms). Edit to suit. */
  static final List<String> BLOCK=List.of("consultancy","consultancies","consulting","consultants","staffing","recruitment","recruiters","manpower","placement","outsourcing","headhunt");
  /** Bulk import: CSV with header, columns name,website,careersUrl,country,industry (no quoted commas). Rows need a name and an http(s) website, duplicates are skipped. */
  @PostMapping("/admin/companies/import") Map<String,Object> importCsv(@RequestParam("file") org.springframework.web.multipart.MultipartFile f) throws Exception {
    Set<String> seen=new HashSet<>(jdbc.queryForList("select lower(name) from companies",String.class)); int ok=0,skip=0; List<Object[]> batch=new ArrayList<>();
    String sql="insert ignore into companies(name,website,careers_url,country,industry) values(?,?,?,?,?)";
    try(java.io.BufferedReader br=new java.io.BufferedReader(new java.io.InputStreamReader(f.getInputStream(),java.nio.charset.StandardCharsets.UTF_8))){
      String line=br.readLine();
      while((line=br.readLine())!=null){ String[] p=line.split(",",-1); String name=p.length>0?p[0].trim():"", site=p.length>1?p[1].trim():"", low=name.toLowerCase();
        if(name.isEmpty()||!site.startsWith("http")||BLOCK.stream().anyMatch(low::contains)||!seen.add(low)){ skip++; continue; }
        batch.add(new Object[]{name,site,p.length>2&&p[2].trim().startsWith("http")?p[2].trim():null,p.length>3?p[3].trim():null,p.length>4?p[4].trim():null}); ok++;
        if(batch.size()>=2000){ jdbc.batchUpdate(sql,batch); batch.clear(); } } }
    if(!batch.isEmpty()) jdbc.batchUpdate(sql,batch); return Map.of("imported",ok,"skipped",skip); }
  @PostMapping("/admin/companies") Map<String,Object> saveCompany(@Valid @RequestBody CompanyReq r){
    Company c=companies.findByNameIgnoreCase(r.name().trim()).orElseGet(Company::new); c.name=r.name().trim(); c.logoUrl=r.logoUrl(); c.website=r.website(); c.careersUrl=r.careersUrl(); c.country=r.country(); c.industry=r.industry(); return cm(companies.save(c)); }
  @DeleteMapping("/admin/companies/{id}") void deleteCompany(@PathVariable Long id){ companies.deleteById(id); }
}

/** On first start (no sample rows yet) loads companies.csv and jobs.csv from resources/seed. Companies are inserted with INSERT IGNORE so existing ones are kept. */
@Component class SeedLoader implements org.springframework.boot.CommandLineRunner {
  final org.springframework.jdbc.core.JdbcTemplate jdbc; final OppRepo opps; SeedLoader(org.springframework.jdbc.core.JdbcTemplate j,OppRepo o){jdbc=j;opps=o;}
  static final String SRC="Sample data";
  public void run(String... a) throws Exception {
    Integer n=jdbc.queryForObject("select count(*) from opportunities where source_name=?",Integer.class,SRC); if(n!=null&&n>0) return;
    seedCompanies(); seedJobs(); }
  void seedCompanies() throws Exception {
    var in=getClass().getResourceAsStream("/seed/companies.csv"); if(in==null) return; List<Object[]> rows=new ArrayList<>();
    try(var br=new java.io.BufferedReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8))){ br.readLine(); String l;
      while((l=br.readLine())!=null){ String[] p=l.split("\\|",-1); if(p.length<3||p[0].isBlank()) continue; String low=p[0].toLowerCase();
        if(DetailController.BLOCK.stream().anyMatch(low::contains)) continue; rows.add(new Object[]{p[0].trim(),p[1].trim(),p[2].trim()}); } }
    if(!rows.isEmpty()) jdbc.batchUpdate("insert ignore into companies(name,industry,country) values(?,?,?)",rows); }
  void seedJobs() throws Exception {
    var in=getClass().getResourceAsStream("/seed/jobs.csv"); if(in==null) return; List<Opportunity> list=new ArrayList<>();
    try(var br=new java.io.BufferedReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8))){ br.readLine(); String l;
      while((l=br.readLine())!=null){ String[] p=l.split("\\|",-1); if(p.length<11) continue; Opportunity o=new Opportunity();
        o.sourceId=p[0]; o.company=p[1]; o.title=p[2]; o.category=p[3]; o.location=p[4]; o.workMode=p[5]; o.stipend=p[6]; o.experience=p[7]; o.requiredSkills=p[8]; o.description=p[9];
        o.sourceName=SRC; o.verified=false; o.ppo=false; o.status="ACTIVE"; o.updatedAt=LocalDateTime.now().minusDays(Integer.parseInt(p[10].trim())); list.add(o); } }
    opps.saveAll(list); }
}
