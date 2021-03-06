

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

import org.sat4j.specs.*;
import org.sat4j.minisat.*;
import org.sat4j.reader.*;


/**
*  This agent performs a sequence of movements, and after each
*  movement it "senses" from the evironment the resulting position
*  and then the outcome from the smell sensor, to try to locate
*  the position of Treasure
*
**/
public class TreasureFinder  {


/**
  * The list of steps to perform
**/
    ArrayList<Position> listOfSteps;
/**
* index to the next movement to perform, and total number of movements
**/
    int idNextStep, numMovements;
/**
*  Array of clauses that represent conclusiones obtained in the last
* call to the inference function, but rewritten using the "past" variables
**/
    ArrayList<VecInt> futureToPast = null;
/**
* the current state of knowledge of the agent (what he knows about
* every position of the world)
**/
    TFState tfstate;
/**
*   The object that represents the interface to the Treasure World
**/
   TreasureWorldEnv EnvAgent;
/**
*   SAT solver object that stores the logical boolean formula with the rules
*   and current knowledge about not possible locations for Treasure
**/
    ISolver solver;
/**
*   Agent position in the world and variable to record if there is a pirate
    at that current position
**/
    int agentX, agentY, pirateFound;
/**
*  Dimension of the world and total size of the world (Dim^2)
**/
    int WorldDim, WorldLinealDim;

/**
*    This set of variables CAN be use to mark the beginning of different sets
*    of variables in your propositional formula (but you may have more sets of
*    variables in your solution).
**/
    int TreasurePastOffset;
    int TreasureFutureOffset;
    int DetectorOffset;
    int actualLiteral;


   /**
     The class constructor must create the initial Boolean formula with the
     rules of the Treasure World, initialize the variables for indicating
     that we do not have yet any movements to perform, make the initial state.

     @param WDim the dimension of the Treasure World

   **/
    public   TreasureFinder(int WDim)
    {

        WorldDim = WDim;
        WorldLinealDim = WorldDim * WorldDim;

        try {
            solver = buildGamma();
        } catch (FileNotFoundException ex) {
            Logger.getLogger(TreasureFinder.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException | ContradictionException ex) {
            Logger.getLogger(TreasureFinder.class.getName()).log(Level.SEVERE, null, ex);
        }
        numMovements = 0;
        idNextStep = 0;
        System.out.println("STARTING TREASURE FINDER AGENT...");


        tfstate = new TFState(WorldDim);  // Initialize state (matrix) of knowledge with '?'
        tfstate.printState();
    }

    /**
      Store a reference to the Environment Object that will be used by the
      agent to interact with the Treasure World, by sending messages and getting
      answers to them. This function must be called before trying to perform any
      steps with the agent.

      @param environment the Environment object

    **/
    public void setEnvironment( TreasureWorldEnv environment ) {

         EnvAgent =  environment;
    }


    /**
      Load a sequence of steps to be performed by the agent. This sequence will
      be stored in the listOfSteps ArrayList of the agent.  Steps are represented
      as objects of the class Position.

      @param numSteps number of steps to read from the file
      @param stepsFile the name of the text file with the line that contains
                       the sequence of steps: x1,y1 x2,y2 ...  xn,yn

    **/
    public void loadListOfSteps( int numSteps, String stepsFile )
    {
        String[] stepsList;
        String steps = ""; // Prepare a list of movements to try with the FINDER Agent
        try {
            BufferedReader br = new BufferedReader(new FileReader(stepsFile));
            System.out.println("STEPS FILE OPENED ...");
            steps = br.readLine();
            br.close();
        } catch (FileNotFoundException ex) {
            System.out.println("MSG.   => Steps file not found");
            exit(1);
        } catch (IOException ex) {
            Logger.getLogger(TreasureFinder.class.getName()).log(Level.SEVERE, null, ex);
            exit(2);
        }
        stepsList = steps.split(" ");
        listOfSteps = new ArrayList<Position>(numSteps);
        for (int i = 0 ; i < numSteps ; i++ ) {
            String[] coords = stepsList[i].split(",");
            listOfSteps.add(new Position(Integer.parseInt(coords[0]), Integer.parseInt(coords[1])));
        }
        numMovements = listOfSteps.size(); // Initialization of numMovements
        idNextStep = 0;
    }

    /**
     *    Returns the current state of the agent.
     *
     *    @return the current state of the agent, as an object of class TFState
    **/
    public TFState getState()
    {
        return tfstate;
    }

    /**
    *    Execute the next step in the sequence of steps of the agent, and then
    *    use the agent sensor to get information from the environment. In the
    *    original Treasure World, this would be to use the Smelll Sensor to get
    *    a binary answer, and then to update the current state according to the
    *    result of the logical inferences performed by the agent with its formula.
    *
    **/
    public void runNextStep() throws
            IOException,  ContradictionException, TimeoutException
    {
          pirateFound = 0;
          // Add the conclusions obtained in the previous step
          // but as clauses that use the "past" variables
          addLastFutureClausesToPastClauses();

          // Ask to move, and check whether it was successful
          // Also, record if a pirate was found at that position
          processMoveAnswer( moveToNext( ) );


          // Next, use Detector sensor to discover new information
          processDetectorSensorAnswer( DetectsAt() );
          // If a pirate was found at new agent position, ask question to
          // pirate and process Answer to discover new information
          if (pirateFound == 1) {
             processPirateAnswer( IsTreasureUpOrDown() );
          }

          // Perform logical consequence questions for all the positions
          // of the Treasure World
          performInferenceQuestions();
          tfstate.printState();      // Print the resulting knowledge matrix
    }


    /**
    *   Ask the agent to move to the next position, by sending an appropriate
    *   message to the environment object. The answer returned by the environment
    *   will be returned to the caller of the function.
    *
    *   @return the answer message from the environment, that will tell whether the
    *           movement was successful or not.
    **/
    public AMessage moveToNext()
    {
        Position nextPosition;

        if (idNextStep < numMovements) {
            nextPosition = listOfSteps.get(idNextStep);
            idNextStep = idNextStep + 1;
            return moveTo(nextPosition.x, nextPosition.y);
        } else {
            System.out.println("NO MORE steps to perform at agent!");
            return (new AMessage("NOMESSAGE","","",""));
        }
    }

    /**
    * Use agent "actuators" to move to (x,y)
    * We simulate this why telling to the World Agent (environment)
    * that we want to move, but we need the answer from it
    * to be sure that the movement was made with success
    *
    *  @param x  horizontal coordinate of the movement to perform
    *  @param y  vertical coordinate of the movement to perform
    *
    *  @return returns the answer obtained from the environment object to the
    *           moveto message sent
    **/
    public AMessage moveTo( int x, int y )
    {
        // Tell the EnvironmentAgentID that we want to move
        AMessage msg, ans;

        msg = new AMessage("moveto", (new Integer(x)).toString(), (new Integer(y)).toString(), "");
        ans = EnvAgent.acceptMessage( msg );
        System.out.println("FINDER => moving to : (" + x + "," + y + ")");

        return ans;
    }

   /**
     * Process the answer obtained from the environment when we asked
     * to perform a movement
     *
     * @param moveans the answer given by the environment to the last move message
   **/
    public void processMoveAnswer ( AMessage moveans )
    {
        if ( moveans.getComp(0).equals("movedto") ) {
          agentX = Integer.parseInt( moveans.getComp(1) );
          agentY = Integer.parseInt( moveans.getComp(2) );
          pirateFound = Integer.parseInt( moveans.getComp(3) );
          System.out.println("FINDER => moved to : (" + agentX + "," + agentY + ")" + " Pirate found : "+pirateFound );
        }
    }

    /**
     *   Send to the environment object the question:
     *   "Does the detector sense something around(agentX,agentY) ?"
     *
     *   @return return the answer given by the environment
    **/
    public AMessage DetectsAt( )
    {
        AMessage msg, ans;

        msg = new AMessage( "detectsat", (new Integer(agentX)).toString(),
                                       (new Integer(agentY)).toString(), "" );
        ans = EnvAgent.acceptMessage( msg );
        System.out.println("FINDER => detecting at : (" + agentX + "," + agentY + ")");
        return ans;
    }

    /**
    *   Process the answer obtained for the query "Detects at (x,y)?"
    *   by adding the appropriate evidence clause to the formula
    *
    *   @param ans message obtained to the query "Detects at (x,y)?".
    *          It will a message with three fields: [0,1,2,3] x y
    **/
    public void processDetectorSensorAnswer( AMessage ans ) throws
            IOException, ContradictionException,  TimeoutException
    {
        int x = Integer.parseInt(ans.getComp(1));
        int y = Integer.parseInt(ans.getComp(2));
        String detects = ans.getComp(0);

        // Call your function/functions to add the evidence clauses
        // to Gamma to then be able to infer new NOT possible positions

        // CALL your functions HERE
        getDetectorSensorClauses(x, y, detects);
    }

    /**
    * Adds the clauses obtained with the metal sensor information to the formula Gamma.
    *
    * @param x        x coordinate of position.
    * @param y        y coordinate of position.
    * @param reading  metal sensor can give four different readings: 0, 1, 2 or 3.
    * @throws ContradictionException if inserting contradictory clauses in formula.
    **/
    private void getDetectorSensorClauses(int x, int y, String reading) throws ContradictionException {
      System.out.println("Metal sensor returned: " + reading);
      System.out.println("Inserting evidence clause");
      switch (reading) {

        case "0":
          getSensorClauses0();
          break;

        case "1":
          getSensorClauses1();
          break;

        case "2":
          getSensorClauses2();
          break;

        case "3":
          getSensorClauses3();
          break;

        default:
          System.out.println("FINDER => Error with metal sensor reading");
          break;
      }
    }

    /**
    * Add denied clauses that are within the range of readings 1, 2 and 3
    *
    * @throws ContradictionException if inserting contradictory clauses in formula.
    **/
    private void getSensorClauses0() throws ContradictionException{
        for (int x=1; x<=WorldDim; x++){
            for (int y=1; y<=WorldDim; y++){
                VecInt clause = new VecInt();
                if (agentX-2<=x && x<=agentX+2 && agentY-2<=y && y<=agentY+2){
                  int linealIndex = -(coordToLineal(x, y, TreasureFutureOffset));
                  clause.insertFirst(linealIndex);
                  solver.addClause(clause);
                }
            }
        }
    }

    /**
    * Add denied clauses that are within the range of the readings 1
    *
    * @throws ContradictionException if inserting contradictory clauses in formula.
    **/
    private void getSensorClauses1() throws ContradictionException{
        for (int x=1; x<=WorldDim; x++){
            for (int y=1; y<=WorldDim; y++){
                VecInt clause = new VecInt();
                if (x==agentX && y==agentY){
                    // Tiles where the treasure is located
                }else{
                    int linealIndex = -(coordToLineal(x, y, TreasureFutureOffset));
                    clause.insertFirst(linealIndex);
                    solver.addClause(clause);
                }
            }
        }
    }

    /**
    * Add denied clauses that are within the range of the readings 2
    *
    * @throws ContradictionException if inserting contradictory clauses in formula.
    **/
    private void getSensorClauses2() throws ContradictionException {
        for (int x=1; x<=WorldDim; x++){
            for (int y=1; y<=WorldDim; y++){
                VecInt clause = new VecInt();
                if (  (x==agentX-1 && y==agentY+1) || (x==agentX && y==agentY+1) || (x==agentX+1 && y==agentY+1) ||
                      (x==agentX-1 && y==agentY  )                               || (x==agentX+1 && y==agentY  ) ||
                      (x==agentX-1 && y==agentY-1) || (x==agentX && y==agentY-1) || (x==agentX+1 && y==agentY-1)
                    ){
                    // Tiles where the treasure is located
                }else{
                    int linealIndex = -(coordToLineal(x, y, TreasureFutureOffset));
                    clause.insertFirst(linealIndex);
                    solver.addClause(clause);
                }
            }
        }
    }

    /**
    * Add denied clauses that are within the range of the readings 3
    *
    * @throws ContradictionException if inserting contradictory clauses in formula.
    **/
    private void getSensorClauses3() throws ContradictionException {
        for (int x=1; x<=WorldDim; x++){
            for (int y=1; y<=WorldDim; y++){
                VecInt clause = new VecInt();
                if (  (x==agentX-2 && y==agentY+2) || (x==agentX-1 && y==agentY+2) || (x==agentX && y==agentY+2) || (x==agentX+1 && y==agentY+2) || (x==agentX+2 && y==agentY+2) ||
                      (x==agentX-2 && y==agentY+1)                                                                                               || (x==agentX+2 && y==agentY+1) ||
                      (x==agentX-2 && y==agentY  )                                                                                               || (x==agentX+2 && y==agentY  ) ||
                      (x==agentX-2 && y==agentY-1)                                                                                               || (x==agentX+2 && y==agentY-1) ||
                      (x==agentX-2 && y==agentY-2) || (x==agentX-1 && y==agentY-2) || (x==agentX && y==agentY-2) || (x==agentX+1 && y==agentY-2) || (x==agentX+2 && y==agentY-2)
                    ){
                    // Tiles where the treasure is located
                }else{
                    int linealIndex = -(coordToLineal(x, y, TreasureFutureOffset));
                    clause.insertFirst(linealIndex);
                    solver.addClause(clause);
                }
            }
        }
    }

    /**
     *   Send to the pirate (using the environment object) the question:
     *   "Is the treasure up or down of (agentX,agentY)  ?"
     *
     *   @return return the answer given by the pirate
    **/
    public AMessage IsTreasureUpOrDown()
    {
        AMessage msg, ans;

        msg = new AMessage( "treasureup", (new Integer(agentX)).toString(),
                                         (new Integer(agentY)).toString(), "" );
        ans = EnvAgent.acceptMessage( msg );
        System.out.println("FINDER => checking treasure up of : (" + agentX + "," + agentY + ")");
        return ans;
    }


    /**
    * Call a specific function, depending on the pirate answer, to add the evidence
    * clauses to Gamma to then be able to infer new NOT possible positions
    *
    * @throws ContradictionException if inserting contradictory clauses in formula.
    **/
    public void processPirateAnswer(AMessage ans) throws ContradictionException
    {
        int y = Integer.parseInt(ans.getComp(2));
        String isup = ans.getComp(0);
        // isup should be either "yes" (is up of agent position), or "no"
        // Call your function/functions to add the evidence clauses
        // to Gamma to then be able to infer new NOT possible positions
        if (isup=="yes"){
            getPirateClausesUp( y, isup);
        }
        else{
            getPirateClausesDown( y, isup);
        }
    }

    /**
    * Add all positions above the agent's current position as clauses
    *
    * @throws ContradictionException if inserting contradictory clauses in formula.
    **/
    private void getPirateClausesUp(int y, String isup) throws ContradictionException {
        for (int i=1; i<=y; i++){
            addLine(i);
        }
    }

    /**
    * Add all positions below the agent's current position as clauses
    *
    * @throws ContradictionException if inserting contradictory clauses in formula.
    **/
    private void getPirateClausesDown(int y, String isup) throws ContradictionException {
        for (int i=y+1; i<=WorldDim; i++){
            addLine(i);
        }
    }

    /**
    * Add all the clauses of the row indicated as parameter
    *
    * @param i        y coordinate of the row.
    * @throws ContradictionException if inserting contradictory clauses in formula.
    **/
    private void addLine(int i) throws ContradictionException {
        for (int x=0; x<WorldDim;x++){
            VecInt clause = new VecInt();
            int linealIndex = -(coordToLineal(x, i, TreasureFutureOffset));
            clause.insertFirst(linealIndex);
            solver.addClause(clause);
        }
    }

    /**
    *  This function should add all the clauses stored in the list
    *  futureToPast to the formula stored in solver.
    *   Use the function addClause( VecInt ) to add each clause to the solver
    *
    **/
    public void addLastFutureClausesToPastClauses() throws  IOException,
            ContradictionException, TimeoutException
    {
      if (futureToPast != null) {
			     for (VecInt vecInt : futureToPast) {
				         solver.addClause(vecInt);
			     }
		  }
    }

    /**
    * This function should check, using the future variables related
    * to possible positions of Treasure, whether it is a logical consequence
    * that Treasure is NOT at certain positions. This should be checked for all the
    * positions of the Treasure World.
    * The logical consequences obtained, should be then stored in the futureToPast list
    * but using the variables corresponding to the "past" variables of the same positions
    *
    * An efficient version of this function should try to not add to the futureToPast
    * conclusions that were already added in previous steps, although this will not produce
    * any bad functioning in the reasoning process with the formula.
    **/
    public void  performInferenceQuestions() throws  IOException,
            ContradictionException, TimeoutException
    {
        int posibles=0, tx=0, ty=0;
        futureToPast = new ArrayList<>();
    	  for (int x = 1; x <= WorldDim; x++) {
    		    for (int y = 1; y <= WorldDim; y++) {
                // Get variable number for position i,j in past variables
                int linealIndex = coordToLineal(x, y, TreasureFutureOffset);
                VecInt variablePositive = new VecInt();
                variablePositive.insertFirst(linealIndex);

                // Check if Gamma + variablePositive is unsatisfiable:
          			if (!(solver.isSatisfiable(variablePositive))) {
          				  tfstate.set(y,x, "X");
          			}else{
                    tfstate.set(y,x,"?");
                    posibles++; tx=x; ty=y;
                }
    		}
        }
        if(posibles==1){
            System.out.println("Treasure: -> ("+tx+","+ty+")");
            tfstate.set(ty,tx,"?");
        }
    }

    /**
    * This function builds the initial logical formula of the agent and stores it
    * into the solver object.
    *
    *  @return returns the solver object where the formula has been stored
    **/
    public ISolver buildGamma() throws UnsupportedEncodingException,
            FileNotFoundException, IOException, ContradictionException
    {
        int totalNumVariables;

        // You must set this variable to the total number of boolean variables
        // in your formula Gamma
        totalNumVariables = WorldLinealDim;
        solver = SolverFactory.newDefault();
        solver.setTimeout(3600);
        solver.newVar(totalNumVariables);
        // This variable is used to generate, in a particular sequential order,
        // the variable indentifiers of all the variables
        actualLiteral = 1;

        // call here functions to add the different sets of clauses
        // of Gamma to the solver object
        pastTreasure(); // Treasure t-1, from 1,1 to n,n (1 clause)
		    futureTreasure(); // Treasure t+1, from 1,1 to n,n (1 clause)
		    pastTreasureToFutureTreasure(); // Treasure t-1 -> Treasure t+1 (nxn clauses)

        return solver;
    }

    /**
	 * Adds the clause that says that the treasure must be in some position
	 * with respect to the variables that talk about past positions.
	 *
	 * @throws ContradictionException if inserting contradictory clauses in formula (solver).
	 **/
  	private void pastTreasure() throws ContradictionException {
  		TreasurePastOffset = actualLiteral;
  		VecInt pastClause = new VecInt();
  		for (int i = 0; i < WorldLinealDim; i++) {
  			pastClause.insertFirst(actualLiteral);
  			actualLiteral++;
  		}
  		solver.addClause(pastClause);
  	}

    /**
  	 * Adds the clause that says that the treasure must be in some position
  	 * with respect to the variables that talk about past positions.
  	 *
  	 * @throws ContradictionException if inserting contradictory clauses in formula (solver).
  	 **/
    	private void futureTreasure() throws ContradictionException {
    		TreasureFutureOffset = actualLiteral;
    		VecInt futureClause = new VecInt();
    		for (int i = 0; i < WorldLinealDim; i++) {
    			futureClause.insertFirst(actualLiteral);
    			actualLiteral++;
    		}
    		solver.addClause(futureClause);
    	}

      /**
  	 * Adds the clauses that say that if in the past we reached the conclusion
  	 * that Treasure cannot be in a position (x,y), then this should be also true
  	 * in the future.
  	 *
  	 * @throws ContradictionException if inserting contradictory clauses in formula (solver).
  	 **/
    	private void pastTreasureToFutureTreasure() throws ContradictionException {
    		for (int i = 0; i < WorldLinealDim; i++) {
    			VecInt clause = new VecInt();
    			clause.insertFirst(i + 1);
    			clause.insertFirst(-(i + TreasureFutureOffset));
    			solver.addClause(clause);
    		}
    	}

     /**
     * Convert a coordinate pair (x,y) to the integer value  t_[x,y]
     * of variable that stores that information in the formula, using
     * offset as the initial index for that subset of position variables
     * (past and future position variables have different variables, so different
     * offset values)
     *
     *  @param x x coordinate of the position variable to encode
     *  @param y y coordinate of the position variable to encode
     *  @param offset initial value for the subset of position variables
     *         (past or future subset)
     *  @return the integer indentifer of the variable  b_[x,y] in the formula
    **/
    public int coordToLineal(int x, int y, int offset) {
        return ((x - 1) * WorldDim) + (y - 1) + offset;
    }

    /**
     * Perform the inverse computation to the previous function.
     * That is, from the identifier t_[x,y] to the coordinates  (x,y)
     *  that it represents
     *
     * @param lineal identifier of the variable
     * @param offset offset associated with the subset of variables that
     *        lineal belongs to
     * @return array with x and y coordinates
    **/
    public int[] linealToCoord(int lineal, int offset)
    {
        lineal = lineal - offset + 1;
        int[] coords = new int[2];
        coords[1] = ((lineal-1) % WorldDim) + 1;
        coords[0] = (lineal - 1) / WorldDim + 1;
        return coords;
    }

}
