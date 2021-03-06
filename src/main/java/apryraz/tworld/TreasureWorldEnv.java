package apryraz.tworld;

import java.util.ArrayList;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import static java.lang.System.exit;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sat4j.core.VecInt;

public class TreasureWorldEnv {
/**
  X,Y position of Treasure and world dimension

**/
  int TreasureX, TreasureY, WorldDim;

  /**
    * The list of pirates
  **/
  ArrayList<Position> listOfPirates;

/**
*  Class constructor
*
* @param dim dimension of the world
* @param tx  X position of Treasure
* @param ty  Y position of Treasure
* @param piratesFile File with list of pirates locations
**/
  public TreasureWorldEnv( int dim, int tx, int ty, String piratesFile ) {

    TreasureX = tx;
    TreasureY = ty;
    WorldDim = dim;
    loadPiratesLocations(piratesFile);
  }

  public TreasureWorldEnv( int dim, int tx, int ty) {

    TreasureX = tx;
    TreasureY = ty;
    WorldDim = dim;
  }


  /**
  *   Load the list of pirates locations
  *
  *    @param piratesFile
  **/
  public void loadPiratesLocations( String piratesFile ) {
    String[] piratesList;
    String pirate = ""; // Prepare a list of movements to try with the FINDER Agent
    try {
        BufferedReader br = new BufferedReader(new FileReader(piratesFile));
        System.out.println("PIRATES FILE OPENED ...");
        pirate = br.readLine();

        br.close();
    } catch (FileNotFoundException ex) {
        System.out.println("MSG.   => Pirates file not found");
        exit(1);
    } catch (IOException ex) {
        Logger.getLogger(TreasureWorldEnv.class.getName()).log(Level.SEVERE, null, ex);
        exit(2);
    }
    piratesList = pirate.split(" ");
    listOfPirates = new ArrayList<Position>();
    for (int i = 0 ; i < piratesList.length ; i++ ) {
        String[] coords = piratesList[i].split(",");
        listOfPirates.add(new Position(Integer.parseInt(coords[0]), Integer.parseInt(coords[1])));
    }
  }


/**
* Process a message received by the TFinder agent,
* by returning an appropriate answer
* This version only process answers to moveto and detectsat messages
*
* @param   msg message sent by the Agent
*
* @return  a msg with the answer to return to the agent
**/
   public AMessage acceptMessage( AMessage msg ) {
       AMessage ans = new AMessage("voidmsg", "", "", "" );
       msg.showMessage();

       if ( msg.getComp(0).equals("moveto") ) {
           int nx = Integer.parseInt( msg.getComp(1) );
           int ny = Integer.parseInt( msg.getComp(2) );

           if (withinLimits(nx,ny))
           {
             int pirate = isPirateInMyCell( nx,ny );

             ans = new AMessage("movedto",msg.getComp(1),msg.getComp(2),
                                (new Integer(pirate)).toString()  );
           }
           else
             ans = new AMessage("notmovedto",msg.getComp(1),msg.getComp(2), "" );

       } else {

          // YOU MUST ANSWER ALSO TO THE OTHER MESSAGE TYPES:
          //   ( "detectsat", "x" , "y", "" )
          //   ( "treasureup", "x", "y", "" )

          if (msg.getComp(0).equals("detectsat"))
          {
            int nx = Integer.parseInt(msg.getComp(1));
            int ny = Integer.parseInt(msg.getComp(2));
            String sensorReading = returnSensorReading(nx, ny);
            ans = new AMessage(sensorReading, msg.getComp(1), msg.getComp(2), "");

          }
          else if (msg.getComp(0).equals("treasureup"))
          {
            int nx = Integer.parseInt(msg.getComp(1));
            int ny = Integer.parseInt(msg.getComp(2));
            String pirateAnswer = returnPirateAnswer(nx, ny);
            ans = new AMessage(pirateAnswer, msg.getComp(1), msg.getComp(2), "");

          }
      }
      return ans;
   }


  /**  Return metal sensor reading
  *
  * @param x  x coordinate of agent position
  * @param y  y coordinate of agent position
  *
  * @return 1  if the treasure is located in the same tile where the agent is now
  * @return 2  if the treasure is in some tile in the square of length 3 centered around the agent
  * @return 3  if the treasure is in some tile in the square of length 5 centered around the agent
  * @return 0  if the treasure is not in any of the locations indicated by the preavious readings 1, 2 and 3
  **/
  private String returnSensorReading(int x, int y) {
      if (x == TreasureX && y == TreasureY) {
  			return "1";
  		} else if ( (TreasureX-1 <= x && x <= TreasureX+1) && (TreasureY-1 <= y && y <= TreasureY+1) ) {
  			return "2";
  		} else if ( (TreasureX-2 <= x && x <= TreasureX+2) && (TreasureY-2 <= y && y <= TreasureY+2) ) {
  			return "3";
  		} else {
  			return "0";
  		}
  }

  /**  Return pirate answer
  *
  * @param x  x coordinate of agent position
  * @param y  y coordinate of agent position
  *
  * @return yes  if the treasure is up of agent position
  * @return no  if the treasure is down of agent position
  **/
  private String returnPirateAnswer(int x, int y) {
      if (TreasureY > y) {
  			return "yes";
  		}
  		return "no";
  }

  /**  Check if there is a pirate in position (x,y)
  *
  * @param x  x coordinate of agent position
  * @param y  y coordinate of agent position
  *
  * @return 1  if (x,y) contains a pirate, 0 otherwise
  **/
   public int isPirateInMyCell( int x, int y ) {
     for (int i = 0 ; i < listOfPirates.size() ; i++ ) {
       if (x == listOfPirates.get(i).x && y == listOfPirates.get(i).y) {
         return 1;
       }
     }
     return 0;
   }


  /**
  * Check if position x,y is within the limits of the
  * WorldDim x WorldDim   world
  *
  * @param x  x coordinate of agent position
  * @param y  y coordinate of agent position
  *
  * @return true if (x,y) is within the limits of the world
  **/
   public boolean withinLimits( int x, int y ) {

    return ( x >= 1 && x <= WorldDim && y >= 1 && y <= WorldDim);
  }

}
