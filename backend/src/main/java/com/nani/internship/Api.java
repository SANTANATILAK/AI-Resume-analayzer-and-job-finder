package com.nani.internship;
import com.fasterxml.jackson.core.type.TypeReference; import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid; import jakarta.validation.constraints.*;
import java.io.InputStream; import java.time.LocalDate; import java.time.LocalDateTime; import java.util.*; import java.util.regex.Pattern; import java.util.stream.Collectors;
import org.apache.pdfbox.Loader; import org.apache.pdfbox.pdmodel.PDDocument; import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor; import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value; import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication; import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*; import org.springframework.web.multipart.MultipartFile; import org.springframework.web.server.ResponseStatusException;

record RegisterReq(@NotBlank String fullName,@Email @NotBlank String email,@Size(min=8) String password,@NotNull @Min(2020) @Max(2030) Integer gradYear,@NotBlank String branch){}
record LoginReq(@NotBlank String email,@NotBlank String password){}
record OppReq(@NotBlank String sourceId,@NotBlank String company,@NotBlank String title,String category,String description,String requiredSkills,
  String gradYears,String branches,String location,String workMode,String stipend,Boolean ppo,String applyUrl,String sourceName,Boolean verified,String status,LocalDate closingDate){}

@RestController @RequestMapping("/api") class HealthController { @GetMapping("/health") Map<String,String> ok(){ return Map.of("status","ok"); } }

@RestController @RequestMapping("/api/auth") class AuthController {
  final UserRepo users; final ResumeRepo resumes; final PasswordEncoder enc; final JwtUtil jwt; final String adminEmail;
  AuthController(UserRepo u,ResumeRepo r,PasswordEncoder e,JwtUtil j,@Value("${app.admin-email}") String a){users=u;resumes=r;enc=e;jwt=j;adminEmail=a;}
  @PostMapping("/register") Map<String,Object> register(@Valid @RequestBody RegisterReq r){
    String email=r.email().trim().toLowerCase();
    if(users.existsByEmail(email)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Email already registered");
    User u=new User(); u.fullName=r.fullName().trim(); u.email=email; u.passwordHash=enc.encode(r.password());
    u.gradYear=r.gradYear(); u.branch=r.branch(); if(!adminEmail.isBlank()&&email.equalsIgnoreCase(adminEmail)) u.role="ADMIN";
    return session(users.save(u)); }
  @PostMapping("/login") Map<String,Object> login(@Valid @RequestBody LoginReq r){
    User u=users.findByEmail(r.email().trim().toLowerCase()).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid email or password"));
    if(!enc.matches(r.password(),u.passwordHash)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid email or password");
    return session(u); }
  Map<String,Object> session(User u){ return Map.of("token",jwt.make(u.email,u.role),"name",u.fullName,"role",u.role,"hasResume",resumes.findByUserId(u.id).isPresent()); }
}

@RestController @RequestMapping("/api/resume") class ResumeController {
  static final List<String> SKILLS=List.of("python",
    "java",
    "javascript",
    "typescript",
    "react",
    "node.js",
    "spring boot",
    "sql",
    "mysql",
    "postgresql",
    "mongodb",
    "html",
    "css",
    "rest api",
    "git",
    "github",
    "docker",
    "kubernetes",
    "aws",
    "azure",
    "linux",
    "data structures",
    "machine learning",
    "deep learning",
    "artificial intelligence",
    "nlp",
    "computer vision",
    "tensorflow",
    "pytorch",
    "pandas",
    "numpy",
    "scikit-learn",
    "c++",
    "kotlin",
    "flutter",
    "devops",
    "cyber security",
    "excel",
    "power bi",
    "tableau",
    "statistics",
    "financial modeling",
    "accounting",
    "tally",
    "gst",
    "sap",
    "supply chain",
    "digital marketing",
    "seo",
    "content writing",
    "communication",
    "sales",
    "business analysis",
    "project management",
    "figma",
    "ui/ux",
    "photoshop",
    "illustrator",
    "autocad",
    "solidworks",
    "catia",
    "ansys",
    "matlab",
    "simulink",
    "plc",
    "scada",
    "embedded c",
    "arduino",
    "vlsi",
    "verilog",
    "vhdl",
    "pcb design",
    "iot",
    "revit",
    "staad pro",
    "quality control",
    "six sigma",
    "lean manufacturing",
    "cad",
    "cnc",
    "thermodynamics",
    "process design",
    "aspen plus",
    "research",
    "lab techniques",
    "spectroscopy",
    "pcr",
    "clinical research",
    "pharmacology");
  static final Map<String,String> ALIAS=Map.ofEntries(Map.entry("js","javascript"),Map.entry("reactjs","react"),Map.entry("react.js","react"),Map.entry("nodejs","node.js"),Map.entry("node","node.js"),
    Map.entry("springboot","spring boot"),Map.entry("ml","machine learning"),Map.entry("dsa","data structures"),Map.entry("postgres","postgresql"),Map.entry("html5","html"),Map.entry("css3","css"),
    Map.entry("restful","rest api"),Map.entry("rest apis","rest api"),Map.entry("ai","artificial intelligence"),Map.entry("powerbi","power bi"),Map.entry("ui ux","ui/ux"),Map.entry("sklearn","scikit-learn"),Map.entry("cpp","c++"),Map.entry("k8s","kubernetes"));
  static final Map<String,List<String>> SECTIONS=new LinkedHashMap<>(){{ put("education",List.of("education","b.tech","bachelor")); put("projects",List.of("project"));
    put("experience",List.of("experience","internship")); put("skills",List.of("skills")); put("certifications",List.of("certification","certificate")); }};
  final UserRepo users; final ResumeRepo resumes; final ObjectMapper om;
  ResumeController(UserRepo u,ResumeRepo r,ObjectMapper o){users=u;resumes=r;om=o;}

  @PostMapping Map<String,Object> upload(Authentication auth,@RequestParam("file") MultipartFile f) throws Exception {
    User u=users.findByEmail(auth.getName()).orElseThrow();
    if(f.getSize()>5*1024*1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"File is larger than 5 MB");
    String text=extract(f);
    if(text.isBlank()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"No readable text found. Scanned PDFs are not supported yet.");
    Map<String,Object> a=analyze(text);
    Resume r=resumes.findByUserId(u.id).orElseGet(Resume::new);
    r.userId=u.id; r.fileData=f.getBytes(); r.fileName=f.getOriginalFilename(); r.text=text; r.score=(Integer)a.get("score");
    r.skills=String.join(",",(List<String>)a.get("skills")); r.analysisJson=om.writeValueAsString(a); r.uploadedAt=LocalDateTime.now();
    resumes.save(r); return view(r); }
  @GetMapping("/file") org.springframework.http.ResponseEntity<byte[]> file(Authentication auth){
    User u=users.findByEmail(auth.getName()).orElseThrow(); Resume r=resumes.findByUserId(u.id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"No resume uploaded"));
    if(r.fileData==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Original file was not stored. Upload again.");
    return org.springframework.http.ResponseEntity.ok().header("Content-Disposition","attachment; filename=\""+r.fileName.replace("\"","")+"\"").contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM).body(r.fileData); }
  @GetMapping("/me") Map<String,Object> me(Authentication auth) throws Exception {
    User u=users.findByEmail(auth.getName()).orElseThrow();
    return view(resumes.findByUserId(u.id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"No resume uploaded"))); }

  Map<String,Object> view(Resume r) throws Exception {
    Map<String,Object> m=new LinkedHashMap<>(om.readValue(r.analysisJson,new TypeReference<Map<String,Object>>(){}));
    m.put("fileName",r.fileName); m.put("uploadedAt",r.uploadedAt.toString()); return m; }
  String extract(MultipartFile f) throws Exception {
    String n=Optional.ofNullable(f.getOriginalFilename()).orElse("").toLowerCase();
    try(InputStream in=f.getInputStream()){
      if(n.endsWith(".pdf")){ try(PDDocument d=Loader.loadPDF(in.readAllBytes())){ return new PDFTextStripper().getText(d); } }
      if(n.endsWith(".docx")){ try(XWPFDocument d=new XWPFDocument(in); XWPFWordExtractor x=new XWPFWordExtractor(d)){ return x.getText(); } } }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Only PDF or DOCX files are supported"); }
  /** Explainable score: skills up to 40, sections 10 each (50), length 10. Nothing is random. */
  Map<String,Object> analyze(String text){
    String t=text.toLowerCase(); List<String> skills=new ArrayList<>();
    for(String s:SKILLS) if(Pattern.compile("(?<![a-z0-9])"+Pattern.quote(s)+"(?![a-z0-9])").matcher(t).find()) skills.add(s);
    ALIAS.forEach((a,c)->{ if(!skills.contains(c)&&Pattern.compile("(?<![a-z0-9.])"+Pattern.quote(a)+"(?![a-z0-9])").matcher(t).find()) skills.add(c); });
    List<String> found=new ArrayList<>(),missing=new ArrayList<>();
    SECTIONS.forEach((k,v)->{ if(v.stream().anyMatch(t::contains)) found.add(k); else missing.add(k); });
    int words=t.trim().split("\\s+").length, sk=Math.min(40,skills.size()*4), se=found.size()*10, ln=(words>=200&&words<=1200)?10:(words>=100?5:0);
    List<String> tips=new ArrayList<>();
    missing.forEach(s->tips.add("Add a clear \""+s+"\" section."));
    if(skills.size()<8) tips.add("List more relevant technical skills; only "+skills.size()+" recognised skills were found.");
    if(ln<10) tips.add("Aim for roughly 200-1200 words; yours has "+words+".");
    Map<String,Object> m=new LinkedHashMap<>(); m.put("score",sk+se+ln); m.put("skills",skills); m.put("sectionsFound",found); m.put("sectionsMissing",missing);
    m.put("breakdown",Map.of("skills",sk,"sections",se,"length",ln)); m.put("suggestions",tips); m.put("words",words); return m; }
}

@RestController @RequestMapping("/api") class OpportunityController {
  final UserRepo users; final ResumeRepo resumes; final OppRepo opps; final SavedRepo saved;
  OpportunityController(UserRepo u,ResumeRepo r,OppRepo o,SavedRepo s){users=u;resumes=r;opps=o;saved=s;}
  static Set<String> set(String csv){ return csv==null||csv.isBlank()?Set.of():Arrays.stream(csv.split(",")).map(s->s.trim().toLowerCase()).filter(s->!s.isEmpty()).collect(Collectors.toCollection(LinkedHashSet::new)); }

  static int stipendNum(String s){ if(s==null) return 0; java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\d[\\d,]*").matcher(s); if(!m.find()) return 0;
    try{ return Integer.parseInt(m.group().replace(",","")); }catch(Exception e){ return 0; } }
  /** Match % = matched required skills / required skills x 100. It is not a guarantee of selection. */
  @GetMapping("/opportunities") Map<String,Object> list(Authentication auth,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String category,
      @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="12") int size,@RequestParam(defaultValue="false") boolean savedOnly,@RequestParam(defaultValue="false") boolean paid,@RequestParam(defaultValue="false") boolean ppo,@RequestParam(defaultValue="") String workMode,@RequestParam(defaultValue="0") int minMatch,@RequestParam(defaultValue="") String branch,@RequestParam(defaultValue="false") boolean closingSoon,@RequestParam(defaultValue="0") int minStipend,@RequestParam(defaultValue="") String location,@RequestParam(defaultValue="") String sort){
    User u=users.findByEmail(auth.getName()).orElseThrow();
    Set<String> mine=set(resumes.findByUserId(u.id).map(r->r.skills).orElse("")); String ql=q.toLowerCase(); Set<Long> savedIds=saved.findByUserId(u.id).stream().map(s->s.oppId).collect(Collectors.toSet());
    List<Map<String,Object>> rows=new ArrayList<>(); LocalDate today=LocalDate.now();
    for(Opportunity o:opps.findAll()){
      if(!"ACTIVE".equals(o.status)) continue;
      if(!category.isBlank()&&!category.equalsIgnoreCase(o.category)) continue;
      if(savedOnly&&!savedIds.contains(o.id)) continue; if(paid&&(o.stipend==null||o.stipend.isBlank())) continue;
      if(ppo&&!Boolean.TRUE.equals(o.ppo)) continue;
      if(!branch.isBlank()&&!set(o.branches).isEmpty()&&!set(o.branches).contains(branch.toLowerCase())) continue;
      if(closingSoon&&(o.closingDate==null||o.closingDate.isAfter(today.plusDays(14)))) continue; if(minStipend>0&&stipendNum(o.stipend)<minStipend) continue; if(!workMode.isBlank()&&!workMode.equalsIgnoreCase(o.workMode)) continue;
      if(!ql.isBlank()&&!(o.company+" "+o.title+" "+o.requiredSkills).toLowerCase().contains(ql)) continue;
      if(!location.isBlank()&&(o.location==null||!o.location.toLowerCase().contains(location.toLowerCase()))) continue;
      Set<String> req=set(o.requiredSkills),years=set(o.gradYears);
      List<String> matched=req.stream().filter(mine::contains).toList(), missing=req.stream().filter(s->!mine.contains(s)).toList();
      boolean eligible=(years.isEmpty()||years.contains(String.valueOf(u.gradYear)))&&(set(o.branches).isEmpty()||set(o.branches).contains(String.valueOf(u.branch).toLowerCase()));
      boolean expired=o.closingDate!=null&&o.closingDate.isBefore(today);
      boolean canApply=o.applyUrl!=null&&o.applyUrl.startsWith("http")&&Boolean.TRUE.equals(o.verified)&&!expired;
      Map<String,Object> m=new LinkedHashMap<>(); m.put("id",o.id); m.put("saved",savedIds.contains(o.id)); m.put("company",o.company); m.put("title",o.title); m.put("category",o.category); m.put("experience",o.experience);
      m.put("snippet",o.description==null?"":(o.description.length()>170?o.description.substring(0,170)+"…":o.description)); m.put("location",o.location); m.put("workMode",o.workMode); m.put("stipend",o.stipend); m.put("ppo",o.ppo); m.put("source",o.sourceName); m.put("verified",o.verified);
      m.put("closingDate",o.closingDate); m.put("matchPercent",req.isEmpty()?0:matched.size()*100/req.size()); m.put("matchedSkills",matched); m.put("missingSkills",missing);
      m.put("eligible",eligible); m.put("canApply",canApply); m.put("applyUrl",canApply?o.applyUrl:null); m.put("lastUpdated",o.updatedAt.toString()); rows.add(m); }
    rows.removeIf(x->(Integer)x.get("matchPercent")<minMatch);
    if("recent".equals(sort)) rows.sort((a,b)->((String)b.get("lastUpdated")).compareTo((String)a.get("lastUpdated"))); else
    rows.sort((a,b)->{ int e=Boolean.compare((Boolean)b.get("eligible"),(Boolean)a.get("eligible")); return e!=0?e:Integer.compare((Integer)b.get("matchPercent"),(Integer)a.get("matchPercent")); });
    int from=Math.min(page*size,rows.size()), to=Math.min(from+size,rows.size());
    return Map.of("items",rows.subList(from,to),"total",rows.size(),"eligibleTotal",rows.stream().filter(r->(Boolean)r.get("eligible")).count(),
      "page",page,"pages",(rows.size()+size-1)/size); }

  @PostMapping("/admin/opportunities") Map<String,Object> upsert(@Valid @RequestBody OppReq r){
    Opportunity o=opps.findBySourceId(r.sourceId()).orElseGet(Opportunity::new);
    o.sourceId=r.sourceId(); o.company=r.company(); o.title=r.title(); o.category=r.category(); o.description=r.description(); o.requiredSkills=r.requiredSkills();
    o.gradYears=r.gradYears(); o.branches=r.branches(); o.location=r.location(); o.workMode=r.workMode(); o.stipend=r.stipend(); o.ppo=r.ppo(); o.applyUrl=r.applyUrl();
    o.sourceName=r.sourceName(); o.verified=Boolean.TRUE.equals(r.verified())&&r.applyUrl()!=null&&r.applyUrl().startsWith("http"); o.status=r.status()==null?"ACTIVE":r.status(); o.closingDate=r.closingDate(); o.updatedAt=LocalDateTime.now();
    return Map.of("id",opps.save(o).id); }
}
