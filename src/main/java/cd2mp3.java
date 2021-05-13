//
// cd2mp3.java
//

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.cli.*;

/**
 * App che converte file o tracce audio (queste ultime dopo averle estratte)
 * in formato lossy tramite ffmpeg
 *
 * @author maxpat78
 */
class cd2mp3 {

	static String VERSION = "1.019";

	/**
	 * Classe interna che raccoglie i parametri di conversione
	 *
	 */
	static class Params {
		// Parametri della riga di comando
		Path Target;
		String Format;
		int Quality;
		int Preserve;
		// Caratteristiche del file audio
		int Canali;
		int Frequenza;
		int Campione;
		int cdSize;
		List<Integer> cdTracks;
		
		Params() {
			this.Target =  getUserMusicDir();
			this.Format = "mp3";	// MP3
			this.Quality = 6;		// 6 (in base all'encoder lame)
			this.Preserve = 2;		// nome file e cartella di origine (2 elementi)
			this.Canali = 2; 		// canali audio codificati
			this.Frequenza = 44100; // freq. di campionamento
			this.Campione = 2; 		// dimensione del campione (tipicamente 16 bit)
			this.cdSize = 783216000;
			this.cdTracks = new ArrayList<Integer>();
		}
	};
	
	/**
	 * Classe interna che conserva le caratteristiche di una traccia audio
	 * @param start byte iniziale della traccia
	 * @param length lunghezza in byte della traccia
	 * @param title titolo della traccia
	 * @param meta array di informazioni -meta da passare a ffmpeg
	 */
	static class TrackInfo {
		int Start;
		int Length;
		String Title;
		String[] Meta;
		
		TrackInfo(int start, int length, String title, String[] meta) {
			this.Start = start;
			this.Length = length;
			this.Title = title;
			this.Meta = meta;
		}
	};
	
    public static void main(String[] args) throws IOException, InterruptedException, ParseException {

//    	OutputStreamWriter out = new OutputStreamWriter(System.out);
//    	System.out.println(out.getEncoding());
//    	try {
//    	    System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, out.getEncoding()));
//    	} catch (UnsupportedEncodingException e) {
//    	    throw new InternalError("VM does not support mandatory encoding "+out.getEncoding());
//    	}
    	
    	Options options = new Options();
    	
    	options.addOption("h", false, "mostra l'aiuto in linea");
    	options.addOption("v", false, "mostra il numero di versione");
        
    	options.addOption(Option.builder("d")
        		.hasArg()
        		.argName("DESTINAZIONE")
        		.desc(String.format("imposta la directory di destinazione (default: %s). $ indica la directory sorgente", getUserMusicDir()))
        		.build());
    	options.addOption(Option.builder("t")
        		.hasArg()
        		.argName("TIPO")
        		.desc("imposta il tipo di compressione tra MP3, OGG, OGA, M4A (default: MP3)")
        		.build());
    	options.addOption(Option.builder("q")
        		.hasArg()
        		.argName("QUALITA")
        		.desc("imposta la qualità di compressione di ffmpeg (default: 6, relativa a MP3)")
        		.build());
    	options.addOption(Option.builder("p")
        		.hasArg()
        		.argName("N")
        		.desc("preserva N elementi finali del percorso originario (default: 2, nome file e ultima directory)")
        		.build());
    	options.addOption(Option.builder("l")
        		.hasArg()
        		.argName("LISTA")
        		.desc("specifica un intervallo o elenco di tracce da estrarre da un audio cd (p.e. 1,2,9-15)")
        		.build());
    	
    	CommandLineParser parser = new DefaultParser();
    	CommandLine line = null;
    	
    	try {
    		line = parser.parse(options, args);
    	}
    	catch (MissingArgumentException e) {
    		System.out.format("-%s deve avere un parametro!\n", e.getOption().getOpt());
    		System.exit(1);
    	}

    	Params opts = new Params();
    	Set<String> set = new HashSet<> (Arrays.asList("mp3", "m4a", "ogg", "oga"));

        if (line.hasOption("v")) {
            System.out.format("cd2mp3 versione %s\n", VERSION);
            System.exit(0);
        }
    
    	if (line.hasOption("h") || args.length < 1 || line.getArgs().length < 1) {
            int ecode = 0;
        	if (! line.hasOption("h")) {
    			System.out.println("Occorre specificare una o più directory contenenti file audio riconosciuti quali:\nAIF, ALAC, APE, FLAC, M4A, WAV, WV\n");
                ecode=1;
            }
        		
        	HelpFormatter formatter = new HelpFormatter();
        	formatter.printHelp( "cd2mp3 [opzioni] directory ...",
        			"Converte da lossless a lossy una serie di file/tracce audio usando ffmpeg.\nOpzioni:",
        			options,
        			"\nSe trova un file omonimo con estensione .cue, assume si tratti di un CD\ne ne estrae le tracce.\nOgni sotto directory viene esaminata.");        
    		System.exit(ecode);
    	}

		if (line.hasOption("t")) {
			String j = line.getOptionValue("t").toLowerCase();
			if (! set.contains(j)) {
				System.out.format("Formato di conversione non ammesso: %s\n", j);
				System.exit(1);
			}
			else
				opts.Format = j;
		}

		if (line.hasOption("d")) {
			String j = line.getOptionValue("d");
			if (j.isEmpty()) {
				System.out.format("-d dev'essere seguito da una directory di destinazione!\n");
				System.exit(1);
			}

			opts.Target = Path.of(j);

			if (j.charAt(0) == '$') {
				opts.Preserve = 1; // caso speciale: conserva solo il nome di default,
				                   // se codifichiamo nella stessa directory
			}
		}

		if (line.hasOption("p")) {
			int j = Integer.parseInt(line.getOptionValue("p"));
			if (j < 1) {
				System.out.format("Occorre conservare almeno un elemento del Path originario!\n");
				System.exit(1);
			}
			else
				opts.Preserve = j;
		}

		if (line.hasOption("q")) {
			int j = Integer.parseInt(line.getOptionValue("q"));
			if (j < 1) {
				System.out.format("La qualità del file non può essere inferiore a 1!\n");
				System.exit(1);
			}
			else
				opts.Quality = j;
		}
    	
		if (line.hasOption("l")) {
			String j = line.getOptionValue("l");
			if (j.isEmpty()) {
				System.out.format("-l deve specificare singole tracce o intervalli!\n");
				System.exit(1);
			}

			String[] jj = j.split(",");
			for (String sj : jj) {
				if (sj.contains("-")) {
					String[] ab = sj.split("-");
					IntStream sint = IntStream.range(Integer.decode(ab[0]), 1+Integer.decode(ab[1]));
					sint.forEach(x -> opts.cdTracks.add(x));
				}
				else
					opts.cdTracks.add(Integer.decode(sj));
			}
			
			//System.out.println("DBG: tracce= " + opts.cdTracks);
		}

		// Esamina i restanti argomenti (directory)
		//
    	for (String arg : line.getArgs()) {
        	System.out.format("Cerco file audio lossless in \"%s\"... ", arg);
    		Path p = Paths.get(arg);
            PathMatcher m = FileSystems.getDefault()
            		.getPathMatcher("glob:*.{aif,alac,ape,flac,m4a,wav,wv}");

            List<Path> items = null;
            
            try {
	    		items = Files.walk(p)
	                    .filter(x -> (Files.isRegularFile(x) && m.matches(x.getFileName())) )
	                    .sorted(Comparator.comparing(x -> x.toString()))
	                    .collect(Collectors.toList());
            } catch (NoSuchFileException nsfe) {
            	System.out.println("errore!");
            	continue;
            }
            
    		if (items.isEmpty()) {
				System.out.print("nessuno!\n");
				continue;
    		}
    		else
				System.out.format("%d file.\n", items.size());
    		
            for (Path x : items) {
            	if (isAudioCdImage(x)) {
            		try {
						getUncompressedCdLength(x, opts);
					} catch (Exception e) {
					}
            		String ext = getFileExtension(x.toString()).orElse("");
                	Path c = Path.of(x.toString().replace(ext.toLowerCase(), "cue"));
                	List<TrackInfo> cue = parseCueSheet(c, opts);
                	extractCdTracks(x, cue, opts);
            		continue;
            	}
            	else
            		convertFile(x, opts);
            }
    	}
    }
    
	/**
	 * Determina l'estensione di un nome di file
	 *
	 * @param name stringa con un percorso/nome di file
	 * @return l'estensione del nome file come <code>Optional</code>
	 */
    static Optional<String> getFileExtension(String name) {
        return Optional.ofNullable(name)
          .filter(f -> f.contains("."))
          .map(f -> f.substring(name.lastIndexOf(".") + 1));
    }
    
	/**
	 * Esegue ffmpeg con la shell di sistema
	 *
	 * @param arr array di stringhe con gli argomenti da passare a ffmpeg
	 * @return un oggetto Process con l'istanza di ffmpeg avviata
	 */
    static Process execCmd(String[] arr) throws IOException {
    	String sShell = "sh", sOpt = "-c";
    	if (System.getProperty("os.name").toLowerCase().startsWith("windows")) { 
    		sShell="cmd"; sOpt= "/c";
    	}
    	
    	String[] o = Stream.of(new String[] {sShell, sOpt, "ffmpeg"}, arr)
    			.flatMap(Stream::of)
    			.toArray(String[]::new);
    	//System.out.format("DBG: execCmd: %s\n", o);
    	
    	Process p = null;
    	try {
    		// La seguente può generare eccezione se ffmpeg non è nel path,
    		// la successiva no, poiché la shell di sistema di regola funziona!
    		Runtime.getRuntime().exec("ffmpeg");
    		p = Runtime.getRuntime().exec(o);
    	} catch (Exception e) {
    		System.out.println("errore esegunedo ffmpeg: PATH?");
    		System.exit(1);
    	}
    	
    	return p;
    }

	/**
	 * Determina la directory regolarmente usata per i file musicali
	 *
	 * @return un <code>Path</code> con la directory da usare per i file musicali
	 */
    static Path getUserMusicDir() {
    	if (System.getProperty("os.name").toLowerCase().startsWith("windows"))
            return Path.of(System.getenv("USERPROFILE"), "Music");
        
        return Path.of(System.getenv("HOME"), "music");
    }

	/**
	 * Determina se il file audio sia un'immagine CD, verificando la
	 * presenza di un CUE sheet omonimo
	 * @param p <code>Path</code> di un file audio lossless
	 * @return un valore <code>boolean</code>
	 */
    static boolean isAudioCdImage(Path p) {
    	String s = getFileExtension(p.toString()).orElse("");
    	if (s.isEmpty())
    		return false;
    	return Files.exists(Path.of(p.toString().replace("."+s, ".cue")));
    }
    
	/**
	 * Analizza l'output di ffmpeg per determinare le caratteristiche e la
	 * durata del file audio e, quindi, la sua lunghezza in byte una volta
	 * decompresso
	 * @param cd <code>Path</code> di un file audio lossless di cui calcolare la dimensione non compressa
	 * @param o parametri <code>Params</code> di conversione
	 * @see #Params
	 */
    static void getUncompressedCdLength(Path cd, Params o) throws InterruptedException, IOException {
    	Process p = execCmd(new String[] {"-i", cd.toString()});
    	p.waitFor();
    	String s = new String(p.getErrorStream().readAllBytes(), "ASCII");
    	//System.out.println("ffmpeg error stream:\n" + s);
    	// Analizza la riga "Duration" dall'output di ffmpeg
        Pattern pa = Pattern.compile("Duration: (\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{2})", Pattern.MULTILINE | Pattern.DOTALL);
    	// Analizza la riga "Stream" dall'output di ffmpeg
        Pattern pa2 = Pattern.compile("Stream .+ Audio.+(\\d{5}) Hz, (stereo|mono), .(\\d{2})", Pattern.MULTILINE | Pattern.DOTALL);
        Matcher ma = pa.matcher(s);
        Matcher ma2 = pa2.matcher(s);
        if (ma2.find()) {
        	o.Frequenza = Integer.parseInt(ma2.group(1));
        	if (ma2.group(2).compareTo("mono") == 0)
        		o.Canali = 1;
        	o.Campione = Integer.parseInt(ma2.group(3)) / 8;
        	//System.out.format("DBG: freq=%d can=%d sample=%d\n", o.Frequenza, o.Canali, o.Campione);
        }

        if (ma.find())
    		o.cdSize = o.Canali * o.Campione * o.Frequenza * (3600*Integer.parseInt(ma.group(1)) +
    				60*Integer.parseInt(ma.group(2)) +
    				Integer.parseInt(ma.group(3))) + 2352*Integer.parseInt(ma.group(4));
    }
    
	/**
	 * Crea il <code>Path</code> di destinazione modificando percorso ed estensione
	 * in base alle opzioni date
	 * @param src <code>Path</code> del file audio lossless da convertire o estrarre
	 * @param o parametri <code>Params</code> di conversione
	 * @see #Params
	 * @return il <code>Path</code> del corrispondente file lossy da creare,
	 * costruito in base ai parametri forniti
	 */
    static Path buildTarget(Path src, Params o) {
    	String ext = getFileExtension(src.toString()).orElse("");
    	Path dst = Path.of(src.toString().replace(ext.toLowerCase(), o.Format));
    	Path Target = o.Target;
    	
    	// Se la destinazione è "$", assume la stessa directory di origine
    	if (Target.toString().charAt(0) == '$')
    		Target = src.getParent();
    	
    	if (Target != Path.of(".")) {
    		String relTarget = dst.getFileName().toString();
    		if (o.Preserve > 1) {
    			int i = dst.getNameCount();
    			// Salva gli ultimi elementi del Path di destinazione
    			// Se -p ne indica di più, li conserva tutti
    			relTarget = dst.subpath(i - Math.min(o.Preserve, i), i).toString();
    		}
    		dst = Target.resolve(relTarget);
    		// Crea le directory intermedie, se necessario
    		try {
    			Files.createDirectories(dst.getParent());
    		} catch (Exception e) {
    			
    		}
    	}
    	return dst;
    }
    
	/**
	 * Converte un singolo file audio
	 * @param src <code>Path</code> del file audio lossless da convertire
	 * @param o parametri <code>Params</code> di conversione
	 * @see #Params
	 */
    static void convertFile(Path src, Params o) {
    	Path dst = buildTarget(src,o);
    	Process p;
    	//String out;
    	
    	System.out.format("%s... ", dst.getFileName());

    	// Non riconverte se già presente
    	if (Files.exists(dst)) {
			System.out.print("presente!\n");
			return;
    	}
    	
    	try {
        	p = execCmd(new String[] {"-i", src.toString(), "-v", "quiet", "-y", "-aq", Integer.toString(o.Quality),
        			"-vn", "-map_metadata", "0:g:0", dst.toString()});
			// SE NON SI CHIUDONO GLI STREAM, FFMPEG NON TERMINA!
			p.getInputStream().close();
			p.getOutputStream().close();
			p.getErrorStream().close();
	    	p.waitFor();
	    	//out = new String(p.getErrorStream().readAllBytes(), "ASCII");
	    	//System.out.format("DBG: %s\n", out);
	    	if (! Files.exists(dst))
	    		throw new Exception();
		} catch (Exception e) {
			System.out.format("errore!\n");
			return;
		}
    	System.out.print("ok\n");
    }
    
	/**
	 * Analizza un CUE sheet e crea una List di informazioni sulle tracce
	 * @param p <code>Path</code> del file CUE da analizzare
	 * @param o parametri <code>Params</code> di conversione
	 * @see #Params
	 * @return una lista di oggetti TrackInfo con le informazioni ricavate sulle tracce
	 * @see TrackInfo
	 */
    static List<TrackInfo> parseCueSheet(Path p, Params o) throws IOException {
    	
    	// .lines() assume implicitamente il set di caratteri UTF-8,
    	// ma noi ignoriamo quello effettivo! L'uso di un set monobyte 
    	// preserva da eccezioni da decodifica, ma non garantisce la correttezza!
    	Stream<String> lines = Files.lines(p, StandardCharsets.ISO_8859_1);
    	
        Pattern paTRACK = Pattern.compile("TRACK\\s+(\\d{2})");
        Pattern paPERFORMER = Pattern.compile("PERFORMER\\s+\"(.+)\"");
        Pattern paTITLE = Pattern.compile("TITLE\\s+\"(.+)\"");
        Pattern paINDEX = Pattern.compile("INDEX 01 (\\d{2}):(\\d{2}):(\\d{2})"); // MM:SS:FF
    	Matcher m;

    	List<TrackInfo> liTracks = new ArrayList<TrackInfo>();
    	
    	int iTrack = 0;
    	//String sPerformer;
    	String sTitle = "";
    	List<String> Metadata = new ArrayList<>();
    	int iIndex = 0;
    	Iterator<String> it = lines.iterator();

    	while (it.hasNext()) {
        	
        	String s = it.next();

        	m = paTRACK.matcher(s);
    		if (m.find()) {
    			iTrack = Integer.valueOf(m.group(1));
    			Metadata.add("-metadata");
    			Metadata.add(String.format("TRCK=%s", iTrack));
    			continue;
    		}
        	m = paTITLE.matcher(s);
    		if (m.find() && iTrack > 0) {
    			sTitle = m.group(1);
    			Metadata.add("-metadata");
    			Metadata.add(String.format("TIT2=%s", m.group(1)));
    			continue;
    		}
        	m = paPERFORMER.matcher(s);
    		if (m.find() && iTrack > 0) {
    			//sPerformer = m.group(1);
    			Metadata.add("-metadata");
    			Metadata.add(String.format("TPE1=%s", m.group(1)));
    			continue;
    		}
        	m = paINDEX.matcher(s);
    		if (m.find()) {
    			int bytesPerSecond = o.Frequenza * o.Campione * o.Canali;
    			// Calcola la posizione in byte da quella canonica minuti:secondi:frame (1 frame = 2352 byte, 1/75")
    			iIndex = bytesPerSecond*(60*Integer.parseInt(m.group(1)) + Integer.parseInt(m.group(2))) + 2352*Integer.parseInt(m.group(3));
    			if (! liTracks.isEmpty()) {
    				int id = liTracks.size()-1;
    				TrackInfo ti = liTracks.get(id);
    				ti.Length = iIndex - ti.Start; // aggiorna con la lunghezza della traccia
    				liTracks.set(id, ti);
    			}
    			liTracks.add(new TrackInfo(iIndex, -1, sTitle, Metadata.toArray(String[]::new)));
    			Metadata.clear();
    		}
    	}
		
		{ 
			int id = liTracks.size()-1;
			TrackInfo ti = liTracks.get(id);
			ti.Length = o.cdSize - ti.Start; // aggiorna con la lunghezza dell'ultima traccia
			liTracks.set(id, ti);
		}
		
		lines.close();
		
		return liTracks;
    }
    
    // 
	/**
	 * Estrae tracce da un CD audio compresso, decomprimendolo con ffmpeg
	 * @param cd <code>Path</code> del file lossless contenente un cd audio
	 * @param cue lista di tracce costruita con parseCueSheet
	 * @see #parseCueSheet
	 * @param o parametri <code>Params</code> di conversione
	 * @see #Params
	 */
    static void extractCdTracks(Path cd, List<TrackInfo> cue, Params o) throws IOException, InterruptedException {

    	System.out.format("Estrazione di %d tracce da %s\n", cue.size(), cd);
    	
    	Process p = null;
    	//String out;
    	
    	// Apre una pipeline per leggere i dati grezzi del cd audio decodificato da ffmpeg
    	p = execCmd(new String[] {"-i", cd.toString(), "-v", "quiet", "-f", "s16le", "-ar", Integer.toString(o.Frequenza) , "-"});
    	
    	//out = new String(p.getErrorStream().readAllBytes(), "ASCII");
    	//System.out.format("DBG: %s\n", out);
    	
    	int i = 1;
    	for (TrackInfo tr : cue) {
    		// Elimina gli eventuali caratteri illeciti nel nome file da formare
    		// e ne trae un Path
    		tr.Title = tr.Title.replaceAll("[\\r\\n\"\\:\\?\\*\\/\\\\]", "");
    		tr.Title = String.format("%02d - %s.%s", i++, tr.Title, o.Format);
    		Path dst = cd.resolveSibling(tr.Title);
    		// Aggiusta il Path in base ai parametri dati
        	dst = buildTarget(dst, o);

        	System.out.format("%s... ", dst.getFileName());
        	// Purtroppo occorre leggere (decomprimere) il dato anche
        	// se non se ne farà uso, per mantenere l'allineamento
    		byte[] raw_track = p.getInputStream().readNBytes(tr.Length);
        	
    		if (raw_track.length == 0) {
    			System.out.print("vuota!\n");
    			continue;
    		}
    		
    		// Non riconverte se gi� presente
        	if (Files.exists(dst)) {
    			System.out.print("presente!\n");
    			continue;
        	}
    		
        	// Comprime la traccia usando una nuova pipeline
        	// "-i -" va specificato DOPO la descrizione dello stream
        	String[] args = Stream.of( new String[] {"-v", "error", "-f", "s16le",
    				"-ar", Integer.toString(o.Frequenza),
    				"-ac", Integer.toString(o.Canali),
    				"-i", "-"},
        			tr.Meta, // EVITARE LA PRESENZA DI APICI, O FFMPEG FALLIRA'!
    				new String[] {dst.toString()} )
        			.flatMap(Stream::of)
        			.toArray(String[]::new);
        	
        	Process p2 = execCmd(args);
        	
    		//out = new String(p2.getErrorStream().readAllBytes(), "ASCII");
        	//System.out.format("DBG: %s\n", out);

        	p2.getOutputStream().write(raw_track);
        	p2.getOutputStream().close();
    		p2.waitFor();

    		System.out.println("ok");
    	}
    }
}
