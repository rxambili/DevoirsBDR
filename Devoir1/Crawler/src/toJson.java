package bdr;


//********************************** Imports ********************************************//
import org.json.JSONObject;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Scanner;
import org.json.JSONException;


public class toJson {
	
	
	
	public static void main(String[] args) throws IOException, JSONException{
		
		
		int j = 1;
		
		//******************** Boucle pour parcourir et récupérer le texte de toutes les pages ********************//
		for( j = 1; j < 1976; j++){
			
			if (j == 1841 || j == 1972 ){ 
				//pages du site sans contenu, ne rien faire.	
			}
			
			else{	
			URL url = new URL("http://www.dxcontent.com/SDB_SpellBlock.asp?SDBID="+j); // URL du site à visiter pour le sort n° i.
			 
			
			//******************** Récupération de tout le contenu de la page **********************//
			URLConnection con = url.openConnection();
			InputStream input = con.getInputStream();
			Scanner s = new Scanner(input);
			s.useDelimiter("iption</div>"); //arrêter le scanner après avoir récupéré l'information qui nous intéresse
			String result = s.hasNext() ? s.next() : "";
			String delims = "<div class='heading'>"; 
			String[] tokens = result.split(delims); //délimiter le contenu restant par un mot clé avant les données intéressantes
			String spell = tokens[1]; // tokens[0] = détails de la page en elle même, tokens[1] = texte sur la page, ce qui nous intéresse.
			s.close();
			
			
			//******************** Isolement du nom du spell **********************//
			
			String name = spell.substring(spell.indexOf("<P>") + 3, spell.indexOf("</p>"));
			
			
			//******************** Isolement du level du spell **********************//
			
			String level = spell.substring(spell.indexOf("Level") + 10, spell.indexOf("<div class='Sep1'>") -4); //isolement de la partie listant les niveaux du spell et leurs casters
			
			boolean isWizard = false; //boolean pour savoir si le sort peut être utilisé par un wizard.
			
			if (spell.contains("wizard")){ //si le sort peut être utilisé par un wizard, on prend le level du sort pour celui-ci.
				String[] spell_levels = level.split(", ");
				for (int i = 0; i<spell_levels.length; i++){
					if (spell_levels[i].contains("wizard")){
						//
						String[] level_wizard = spell_levels[i].split(" ");
						level = level_wizard [1];
						isWizard = true;
					}
				}	
			}
			
			else { //le sort n'est pas pour les wizards, du coup on prend le premier level donné.
				String[] spell_levels = level.split(", ");
				if (spell_levels[0].contains("cleric ")){	//si le sort peut être utilisé par un cleric, il est possible que le format de l'information soit différent sur certaines pages.
					String[] level_first = spell_levels[0].split("/"); //on traite donc spécialement ce cas particulier.
					String[] cler = level_first[0].split(" ");
					level = cler [1];	
				}
				else{	//pour tous les autres sorts
				String[] level_first = spell_levels[0].split(" ");
				level = level_first [1];		
				}
			}
			
			
			//******************** Isolement des composants du spell **********************//
			
			String components = spell.substring(spell.indexOf("Components") + 15, spell.indexOf("Effect") -22 );
			String[] component_table_mid = components.split(", "); //on récupère ce qu'il y a entre les virgules, donc les composants.
			
			String taf = "";
			for (int i = 0; i<component_table_mid.length; i++){
				char first = component_table_mid[i].charAt(0); //on prend juste la première lettre
				String F = Character.toString(first);
				if (F.equals(F.toUpperCase())){	//on filtre pour v oirsi l'on a bien pris que les composants (des fois les composants d'un sort ont une description avec des virgules, on les retire)
				if(i == component_table_mid.length-1){
					taf += F;
				}
				else{
					taf += F +", ";
				}	
				}
			}
			
			
			String[] component_table = taf.split(", "); //table finale avec justeles composants
			
			
			
			//******************** Isolement de la résistance du spell **********************//
			
			
			boolean res = false; //boolean pour indiquer si une résistance du spell existe ou non.
			
			String resistance = null;
			
			if (spell.contains("Resistance")){
				resistance = spell.substring(spell.indexOf("Resistance") + 15, spell.indexOf("</p><div class='Sep1'>Descr") );
				if (resistance.contains("yes")) res = true; //si resistance, res set à true.
			}
		

			//******************** Création du jsonObject **********************//
			
			JSONObject jsonObject = new JSONObject();
			  
		     // ajout du nom, du numero, du niveau et des composants du sort, et on précise si le sort est utilisable part un wizard et s'il possède une résistance
			jsonObject.put("name", name);
			jsonObject.put("number", j);
			jsonObject.put("level", Integer.parseInt(level));
			jsonObject.put("isWizardSpell", isWizard);
			jsonObject.put("components", component_table);
			jsonObject.put("resistance", res);
			
			try{
				//On rajoute au fichier json une string à chaque itération, le true signifie qu'on va rajouter des éléments dans le fichier sans effacer le contenu précédent.
				FileWriter disp = new FileWriter("spells.json",true);
				
				if (j!=1975){
				if (j==1){
					disp.write("[");	
				}
				
				disp.write(jsonObject.toString());
		        disp.write(",");
		        disp.write("\n");
				}
		        if (j==1975){
		        	disp.write(jsonObject.toString());
		        	disp.write("]");	
		        }
		        
		        disp.close();
		    } catch (Exception e) {
		    	
			}
		    }	
		}	
	}
}
