package penta

import PentaBoard
import actions.Action
import com.soywiz.klogger.Logger
import penta.logic.Field
import penta.logic.Field.Goal
import penta.logic.Piece
import penta.network.GameEvent
import penta.util.exhaustive
import penta.util.requireMove

data class BoardState private constructor(
    val players: List<PlayerState> = listOf(),
    val currentPlayer: PlayerState = PlayerState("_", "triangle"),
    val gameType: GameType = GameType.TWO,
    val scoringColors: Map<String, Array<PentaColor>> = mapOf(),
    val figures: List<Piece> = listOf(),
    val positions: Map<String, Field?> = mapOf(),
    val history: List<PentaMove> = listOf(),
    val gameStarted: Boolean = false,
    val turn: Int = 0,
    val forceMoveNextPlayer: Boolean = false,
    val winner: String? = null,
    val selectedPlayerPiece: Piece.Player? = null,
    val selectedBlackPiece: Piece.BlackBlocker? = null,
    val selectedGrayPiece: Piece.GrayBlocker? = null,
    /**
     * true when no gray pieces are in the middle and one from the board has to be be selected
     */
    val selectingGrayPiece: Boolean = false,
    val illegalMove: PentaMove.IllegalMove? = null
) {
    object RemoveIllegalMove
    enum class GameType {
        TWO, THREE, FOUR, TWO_VS_TO
    }

    companion object {
        private val logger = Logger("BoardState")
        fun create(): BoardState {
            logger.info { "created new BoardState" }

            return WithMutableState(
                BoardState()
            ).apply {
                val blacks = (0 until 5).map { i ->
                    Piece.BlackBlocker(
                        "b$i",
                        PentaColor.values()[i],
                        PentaBoard.j[i]
                    ).also {
                        it.position = it.originalPosition
                    }
                }
                val greys = (0 until 5).map { i ->
                    Piece.GrayBlocker(
                        "g$i",
                        PentaColor.values()[i]
                    ).also {
                        it.position = null
                    }
                }
                nextState = nextState.copy(
                    figures = listOf(*blacks.toTypedArray(), *greys.toTypedArray())
                )
            }.nextState
        }

        data class WithMutableState(var nextState: BoardState) {
            val originalState = nextState
            var Piece.position: Field?
                get() = nextState.positions[id]
                set(value) {
                    logger.debug { "move $id to ${value?.id}" }
                    nextState = nextState.copy(positions = nextState.positions + (id to value))
                }
        }

        fun reduceFunc(state: BoardState, action: Any): BoardState {
            logger.info { "action: ${action::class}" }
            return when (action) {
                is org.reduxkotlin.ActionTypes.INIT -> {
                    logger.info { "received INIT" }
                    state
                }
                is org.reduxkotlin.ActionTypes.REPLACE -> {
                    logger.info { "received REPLACE" }
                    state
                }
                is Action<*> -> {
                    reduceFunc(state, action.action)
                }
                is GameEvent -> {
                    reduceFunc(state, action.asMove(state))
                }
                is PentaMove -> {
                    WithMutableState(state).processMove(action)
                }
                is RemoveIllegalMove -> {
                    state.copy(
                        illegalMove = null
                    )
                }
                else -> {
                    error("$action is of unhandled type")
                }
            }
        }

        fun WithMutableState.handleIllegalMove(illegalMove: PentaMove.IllegalMove) {
            nextState = originalState.copy(
                illegalMove = illegalMove
            )
        }

        fun WithMutableState.processMove(move: PentaMove, undo: Boolean = false): BoardState {
            try {
                logger.info { "turn: ${nextState.turn}" }
                logger.info { "currentPlayer: ${nextState.currentPlayer}" }
                logger.info { "processing $move" }
                when (move) {
                    is PentaMove.MovePlayer -> {
                        if (!undo) {
                            with(originalState) {
                                requireMove(gameStarted) {
                                    PentaMove.IllegalMove(
                                        "game is not started yet",
                                        move
                                    )
                                }
                                requireMove(move.playerPiece.playerId == currentPlayer.id) {
                                    PentaMove.IllegalMove(
                                        "move is not from currentPlayer: ${currentPlayer.id}",
                                        move
                                    )
                                }
                                requireMove(canMove(move.from, move.to)) {
                                    PentaMove.IllegalMove(
                                        "no path between ${move.from.id} and ${move.to.id}",
                                        move
                                    )
                                }
                            }

                            val piecesOnTarget = nextState.positions
                                .filterValues {
                                    it == move.to
                                }.keys
                                .mapNotNull { id ->
                                    nextState.figures.find {
                                        it.id == id
                                    }
                                }

                            if (piecesOnTarget.size > 1) {
                                handleIllegalMove(
                                    PentaMove.IllegalMove(
                                        "multiple pieces on target field ${move.to.id}",
                                        move
                                    )
                                )
                                return nextState
                            }

                            val pieceOnTarget = piecesOnTarget.firstOrNull()

                            if (pieceOnTarget != null) {
                                when (pieceOnTarget) {
                                    is Piece.GrayBlocker -> {
                                        logger.info { "taking ${pieceOnTarget.id} off the board" }
                                        pieceOnTarget.position = null
//                                            updatePiecesAtPos(null)
                                    }
                                    is Piece.BlackBlocker -> {
                                        nextState = nextState.copy(
                                            selectedBlackPiece = pieceOnTarget
                                        )
                                        pieceOnTarget.position = null // TODO: set corner field
//                                            updatePiecesAtPos(null)
                                        logger.info { "holding ${pieceOnTarget.id} for repositioning" }
                                    }
                                    else -> {
                                        requireMove(false) {
                                            PentaMove.IllegalMove(
                                                "cannot click on piece type: ${pieceOnTarget::class.simpleName}",
                                                move
                                            )
                                        }
                                    }
                                }
                                // TODO: can unset when pieces float on pointer
//                    pieceOnTarget.position = null
                            } else {
                                // no piece on target field
                            }


                            move.playerPiece.position = move.to //TODO: set in figurePositions

                            postProcess(move)

//                                updatePiecesAtPos(move.from)
//                                updatePiecesAtPos(move.to)
//
//                                updateBoard()

                            logger.info { "append history" }
                            with(nextState) {
                                nextState = nextState.copy(history = history + move)
                            }
                        } else {
                            if (nextState.selectingGrayPiece == true) {
                                nextState = nextState.copy(
                                    selectingGrayPiece = false
                                )
                            }
                            if (nextState.selectedBlackPiece != null) {
                                nextState.selectedBlackPiece!!.position = move.to
                                nextState = nextState.copy(
                                    selectedBlackPiece = null
                                )
                            }
                            if (nextState.selectedGrayPiece != null) {
                                // move back to center / off-board
                                nextState.selectedGrayPiece!!.position = null
                                nextState = nextState.copy(
                                    selectedGrayPiece = null
                                )
                            }

                            move.playerPiece.position = move.from
                            nextState = nextState.copy(
                                selectedPlayerPiece = move.playerPiece,
                                history = nextState.history.dropLast(1),
                                turn = nextState.turn - 1
                            )
                        }
                    }
                    is PentaMove.ForcedPlayerMove -> {
                        if (!undo) {
                            with(originalState) {
                                requireMove(gameStarted) {
                                    PentaMove.IllegalMove(
                                        "game is not started yet",
                                        move
                                    )
                                }
                                requireMove(move.playerPiece.playerId == currentPlayer.id) {
                                    PentaMove.IllegalMove(
                                        "move is not from currentPlayer: ${currentPlayer.id}",
                                        move
                                    )
                                }
                            }
                            val sourceField = move.from
                            if (sourceField is Goal && move.playerPiece.pentaColor == sourceField.pentaColor) {
                                postProcess(move)
                            } else {
                                requireMove(false) {
                                    PentaMove.IllegalMove(
                                        "Forced player move cannot happen",
                                        move
                                    )
                                }
                            }

                            with(nextState) {
                                nextState = nextState.copy(history = history + move)
                            }
                        } else {
                            if (nextState.selectingGrayPiece == true) {
                                nextState = nextState.copy(
                                    selectingGrayPiece = false
                                )
                            }
                            if (nextState.selectedBlackPiece != null) {
                                nextState.selectedBlackPiece!!.position = move.to
                                nextState = nextState.copy(
                                    selectedBlackPiece = null
                                )
                            }
                            if (nextState.selectedGrayPiece != null) {
                                // move back to center / off-board
                                nextState.selectedGrayPiece!!.position = null
                                nextState = nextState.copy(
                                    selectedGrayPiece = null
                                )
                            }

                            move.playerPiece.position = move.from
                            nextState = nextState.copy(
                                selectedPlayerPiece = move.playerPiece,
                                history = nextState.history.dropLast(1),
                                turn = nextState.turn - 1
                            )
                        }
                    }
                    is PentaMove.SwapOwnPiece -> {
                        if (!undo) {
                            with(originalState) {
                                requireMove(gameStarted) {
                                    PentaMove.IllegalMove(
                                        "game is not started yet",
                                        move
                                    )
                                }
                                requireMove(canMove(move.from, move.to)) {
                                    PentaMove.IllegalMove(
                                        "no path between ${move.from.id} and ${move.to.id}",
                                        move
                                    )
                                }
                                requireMove(move.playerPiece.playerId == currentPlayer.id) {
                                    PentaMove.IllegalMove(
                                        "move is not from currentPlayer: ${currentPlayer.id}",
                                        move
                                    )
                                }
                                requireMove(move.playerPiece.position == move.from) {
                                    PentaMove.IllegalMove(
                                        "piece ${move.playerPiece.id} is not on expected position ${move.from.id}",
                                        move
                                    )
                                }
                                requireMove(move.otherPlayerPiece.playerId == currentPlayer.id) {
                                    PentaMove.IllegalMove(
                                        "piece ${move.otherPlayerPiece.id} is not from another player",
                                        move
                                    )
                                }
                                requireMove(move.otherPlayerPiece.position == move.to) {
                                    PentaMove.IllegalMove(
                                        "piece ${move.otherPlayerPiece.id} is not on expected position ${move.to.id}",
                                        move
                                    )
                                }
                            }

                            move.playerPiece.position = move.to
                            move.otherPlayerPiece.position = move.from
//                        updatePiecesAtPos(move.to)
//                        updatePiecesAtPos(move.from)

                            postProcess(move)

                            with(nextState) {
                                nextState = nextState.copy(history = history + move)
                            }
                        } else {
                            move.playerPiece.position = move.from
                            move.otherPlayerPiece.position = move.to

                            nextState = nextState.copy(
                                selectedPlayerPiece = move.playerPiece,
                                history = nextState.history.dropLast(1),
                                turn = nextState.turn - 1
                            )
                        }
                    }
                    is PentaMove.SwapHostilePieces -> {
                        if (!undo) {
                            with(originalState) {
                                requireMove(gameStarted) {
                                    PentaMove.IllegalMove(
                                        "game is not started yet",
                                        move
                                    )
                                }
                                requireMove(canMove(move.from, move.to)) {
                                    PentaMove.IllegalMove(
                                        "no path between ${move.from.id} and ${move.to.id}",
                                        move
                                    )

                                }
                                requireMove(move.playerPiece.playerId == currentPlayer.id) {
                                    PentaMove.IllegalMove(
                                        "move is not from currentPlayer: ${currentPlayer.id}",
                                        move
                                    )
                                }
                                requireMove(move.playerPiece.position == move.from) {
                                    PentaMove.IllegalMove(
                                        "piece ${move.playerPiece.id} is not on expected position ${move.from.id}",
                                        move
                                    )
                                }
                                requireMove(move.otherPlayerPiece.playerId != currentPlayer.id) {
                                    PentaMove.IllegalMove(
                                        "piece ${move.otherPlayerPiece.id} is from another player",
                                        move
                                    )
                                }
                                requireMove(move.otherPlayerPiece.position == move.to) {
                                    PentaMove.IllegalMove(
                                        "piece ${move.otherPlayerPiece.id} is not on expected position ${move.to.id}",
                                        move
                                    )
                                }
                                val lastMove = history.findLast {
                                    it is PentaMove.Move && it.playerPiece.playerId == move.playerPiece.playerId
                                }
                                requireMove(lastMove != move) {
                                    PentaMove.IllegalMove(
                                        "repeating move ${move.asNotation()} is illegal",
                                        move
                                    )
                                }
                            }

                            move.playerPiece.position = move.to
                            move.otherPlayerPiece.position = move.from
//                        updatePiecesAtPos(move.to)
//                        updatePiecesAtPos(move.from)
                            // TODO
                            postProcess(move)
                            with(nextState) {
                                nextState = nextState.copy(history = history + move)
                            }
                        } else {
                            move.playerPiece.position = move.from
                            move.otherPlayerPiece.position = move.to
                            nextState = nextState.copy(
                                selectedPlayerPiece = move.playerPiece
                            )

                            nextState = nextState.copy(
                                history = nextState.history.dropLast(1),
                                turn = nextState.turn - 1
                            )
                        }
                    }
                    is PentaMove.CooperativeSwap -> {
                        TODO("implement cooperative swap")
                        postProcess(move)
                    }

                    is PentaMove.SetBlack -> {
                        if (!undo) {
                            with(originalState) {
                                if (positions.values.any { it == move.to }) {
                                    PentaMove.IllegalMove(
                                        "target position not empty: ${move.to.id}",
                                        move
                                    )
                                }
                                requireMove(move.piece.position == null || move.piece.position == move.from) {
                                    PentaMove.IllegalMove(
                                        "illegal source position of ${move.piece.position?.id}",
                                        move
                                    )
                                }

                                move.piece.position = move.to

                                val lastMove = history.findLast { it !is PentaMove.SetGrey }
                                if (lastMove !is PentaMove.CanSetBlack) {
                                    PentaMove.IllegalMove(
                                        "last move was not the expected move type: ${lastMove!!::class.simpleName} instead of ${PentaMove.CanSetBlack::class.simpleName}",
                                        move
                                    )
                                }
                            }

                            with(nextState) {
                                nextState = nextState.copy(
                                    history = history + move,
                                    selectedBlackPiece = null
                                )
                            }

//                        updatePiecesAtPos(move.to)
//                        updatePiecesAtPos(move.from)
                        } else {

                            move.piece.position = null

                            nextState = nextState.copy(
                                selectedBlackPiece = move.piece,
                                history = nextState.history.dropLast(1),
                                turn = nextState.turn - 1
                            )
                        }
                    }
                    is PentaMove.SetGrey -> {
                        if (!undo) {
                            with(originalState) {
                                if (positions.values.any { it == move.to }) {
                                    handleIllegalMove(
                                        PentaMove.IllegalMove(
                                            "target position not empty: ${move.to.id}",
                                            move
                                        )
                                    )
                                    return nextState
                                }
                                requireMove(move.piece.position == move.from) {
                                    PentaMove.IllegalMove(
                                        "source and target position are the same: ${move.from?.id}",
                                        move
                                    )
                                }
                                logger.debug { "selected: $selectedGrayPiece" }
                                if (selectingGrayPiece) {
                                    requireMove(selectingGrayPiece && move.from != null) {
                                        PentaMove.IllegalMove(
                                            "source is null",
                                            move
                                        )
                                    }
                                } else {
                                    requireMove(selectedGrayPiece == move.piece) {
                                        PentaMove.IllegalMove(
                                            "piece ${move.piece
                                                .id} is not the same as selected gray piece: ${selectedGrayPiece?.id}",
                                            move
                                        )
                                    }
                                }

                                move.piece.position = move.to
                                val lastMove = history.findLast { it !is PentaMove.SetBlack }
                                requireMove(lastMove is PentaMove.CanSetGrey) {
                                    PentaMove.IllegalMove(
                                        "last move was not the expected move type: ${lastMove!!::class.simpleName} instead of ${PentaMove.CanSetGrey::class.simpleName}",
                                        move
                                    )
                                }
                            }


                            with(nextState) {
                                nextState = nextState.copy(
                                    history = history + move,
                                    selectedGrayPiece = null
                                )
                            }

//                        updatePiecesAtPos(move.to)
//                        updatePiecesAtPos(move.from)
                        } else {
                            move.piece.position = null

                            nextState = nextState.copy(
                                selectedGrayPiece = move.piece,
                                history = nextState.history.dropLast(1),
                                turn = nextState.turn - 1
                            )
                        }
                    }
                    is PentaMove.SelectGrey -> {
                        if (!undo) {
                            // TODO: add checks

                            if (move.grayPiece != null) {
                                nextState = nextState.copy(
                                    selectedGrayPiece = move.grayPiece,
                                    selectingGrayPiece = false
                                )
                                move.grayPiece.position = null
                            } else {
                                with(originalState) {
                                    requireMove(selectedGrayPiece != null) {
                                        PentaMove.IllegalMove(
                                            "cannot deselect, since no grey piece is selected",
                                            move
                                        )
                                    }
                                }
                                nextState = nextState.copy(
                                    selectedGrayPiece = null
                                )
                            }

                            with(nextState) {
                                nextState = nextState.copy(
                                    history = history + move
                                )
                            }
                        } else {
                            if (move.grayPiece != null) {
                                move.grayPiece!!.position = move.from
                            } else {
                                TODO("UNDO deselecting gray pieces")
                            }

                            nextState = nextState.copy(
                                selectingGrayPiece = true,
                                selectedGrayPiece = null,
//                                turn = nextState.turn- 1,
                                history = nextState.history.dropLast(1)
                            )
                        }
                    }
                    is PentaMove.SelectPlayerPiece -> {
                        if (!undo) {
                            with(originalState) {
                                if (move.playerPiece != null) {
                                    requireMove(currentPlayer.id == move.playerPiece.playerId) {
                                        PentaMove.IllegalMove(
                                            "selected piece ${move.playerPiece} is not owned by current player ${currentPlayer.id}",
                                            move
                                        )
                                    }
                                } else {
                                    requireMove(selectedPlayerPiece != null) {
                                        PentaMove.IllegalMove(
                                            "cannot deselect because there is no piece selected",
                                            move
                                        )
                                    }
                                }
                            }
                            logger.info { "last before: ${nextState.history.size} ${nextState.history.lastOrNull()}" }
                            nextState = nextState.copy(
                                history = nextState.history + move,
                                selectedPlayerPiece = move.playerPiece
                            )
                            logger.info { "last after: ${nextState.history.size} ${nextState.history.lastOrNull()}" }
                        } else {
                            nextState = nextState.copy(
                                selectedPlayerPiece = move.before,
//                                turn = nextState.turn- 1,
                                history = nextState.history.dropLast(1)
                            )
                        }
                    }
                    is PentaMove.PlayerJoin -> {
                        with(originalState) {
                            requireMove(!gameStarted) {
                                PentaMove.IllegalMove(
                                    "game is already started",
                                    move
                                )
                            }
                            requireMove(players.none { it.id == move.player.id }) {
                                PentaMove.IllegalMove(
                                    "player already joined",
                                    move
                                )
                            }
                        }
                        with(nextState) {
                            nextState = nextState.copy(
                                players = players + move.player
                            )
                        }

                        val playerPieces = (0 until nextState.players.size).flatMap { p ->
                            (0 until 5).map { i ->
                                Piece.Player(
                                    "p$p$i",
                                    nextState.players[p].id,
                                    nextState.players[p].figureId,
                                    PentaColor.values()[i]
                                ).also {
                                    it.position = PentaBoard.c[i]
                                }
                            }
                        }

//                        val removedPositions = originalState.positions.entries - nextState.positions.entries
//                        val addedPositions = nextState.positions.entries - originalState.positions.entries
//                        val changed = originalState.positions.entries.map {
//                            it.key to "${it.value?.id} -> ${nextState.positions[it.key]?.id}"
//                        }.toMap()
//                        logger.info { "removed pos" }
//                        removedPositions.forEach {
//                            logger.warn { "-  pos ${it.key} : ${it.value?.id}" }
//                        }
//                        logger.info { "added pos" }
//                        addedPositions.forEach {
//                            logger.warn { "+  pos ${it.key} : ${it.value?.id}" }
//                        }
//                        logger.info { "changed pos" }
//                        changed.forEach {
//                            logger.warn { "~  pos ${it.key} : ${it.value}" }
//                        }

                        with(nextState) {
                            nextState = nextState.copy(
                                figures = listOf<Piece>(
                                    // keep all black and grey blockers
                                    *figures.filterIsInstance<Piece.BlackBlocker>().toTypedArray(),
                                    *figures.filterIsInstance<Piece.GrayBlocker>().toTypedArray(),
                                    *playerPieces.toTypedArray()
                                )
                            )
                        }

//                        resetPlayers()
//                        updateAllPieces()

                        with(nextState) {
                            nextState = nextState.copy(history = history + move)
                        }
                    }
                    is PentaMove.InitGame -> {
                        with(originalState) {
                            requireMove(!gameStarted) {
                                PentaMove.IllegalMove(
                                    "game is already started",
                                    move
                                )
                            }
                            requireMove(players.isNotEmpty()) {
                                PentaMove.IllegalMove(
                                    "there is no players",
                                    move
                                )
                            }
                        }
                        // TODO: setup UI for players related stuff here

                        // remove old player pieces positions
//                    figures.filterIsInstance<Piece.Player>().forEach {
//                        positions.remove(it.id)
//                    }
//                    figures.filterIsInstance<Piece.BlackBlocker>().forEach {
//                        it.position = it.originalPosition
//                    }
//                    figures.filterIsInstance<Piece.GrayBlocker>().forEach {
//                        it.position = null
//                    }
                        with(nextState) {
                            nextState = nextState.copy(
                                gameStarted = true,
                                turn = 0,
                                history = history + move,
                                currentPlayer = players.first()
                            )
                        }

//                        updateAllPieces()

                        logger.info { "after init: " + nextState.figures.joinToString { it.id } }
                    }
                    // TODO: is this a Move ?
                    is PentaMove.Win -> {
                        // TODO handle win
                        // TODO: set game to not running

                        with(nextState) {
                            nextState = nextState.copy(
                                history = history + move
                            )
                        }
                    }
                    is PentaMove.IllegalMove -> {
                        logger.error {
                            "illegal move: $move"
                        }
                        handleIllegalMove(move)
                    }
                    is PentaMove.Undo -> {
                        move.moves.forEach { reverseNotation ->
                            logger.info { "reverseNotation $reverseNotation" }
                            val toReverseMove = reverseNotation.asMove(nextState)
                            requireMove(nextState.history.last() == toReverseMove) {
                                PentaMove.IllegalMove("cannot undo move $toReverseMove", move)
                            }
                            nextState = processMove(toReverseMove, true)
                        }
                    }
                }.exhaustive

                if (undo || move is PentaMove.Undo) {
                    // early return, skip rest
                    logger.info { "finished undoing $move" }
                    return nextState
                }

                checkWin()

                with(nextState) {
                    if (selectedBlackPiece == null && selectedGrayPiece == null && !selectingGrayPiece
                        && move !is PentaMove.InitGame
                        && move !is PentaMove.PlayerJoin
                        && move !is PentaMove.Win
                        && move !is PentaMove.SelectPlayerPiece
                        && move !is PentaMove.SelectGrey
                        && winner == null
                        && !undo
                    ) {
                        logger.info { "incrementing turn after ${move.asNotation()}" }
                        nextState = nextState.copy(
                            currentPlayer = players[(turn + 1) % players.count()],
                            turn = turn + 1
                        )
                    }
                }
//        if(forceMoveNextPlayer) {
                forceMovePlayerPiece(nextState.currentPlayer)
//        }

//                updateBoard()
            } catch (e: IllegalMoveException) {
                handleIllegalMove(e.move)
            } catch (e: Exception) {
                logger.error { e }
            }
            return nextState
        }

        /**
         * check if the moved piece is on a exit point
         */
        fun WithMutableState.postProcess(move: PentaMove.Move) {
            nextState = nextState.copy(selectedPlayerPiece = null)

            val targetField = move.to
            logger.debug { "playerColor = ${move.playerPiece.pentaColor}" }
            logger.debug {
                if (targetField is Goal) {
                    "pentaColor = ${targetField.pentaColor}"
                } else {
                    "not a JointField"
                }
            }
            if (targetField is Goal && targetField.pentaColor == move.playerPiece.pentaColor) {
                // take piece off the board
                move.playerPiece.position = null

                val colors = nextState.figures
                    .filterIsInstance<Piece.Player>()
                    .filter { it.playerId == move.playerPiece.playerId }
                    .filter { it.position == null }
                    .map { it.pentaColor }
                    .toTypedArray()

                val selectedGrayPiece = nextState.positions.filterValues { it == null }
                    .keys.map { id -> nextState.figures.find { it.id == id } }
                    .filterIsInstance<Piece.GrayBlocker>()
                    .firstOrNull()

                nextState = nextState.copy(
                    scoringColors = nextState.scoringColors + (move.playerPiece.playerId to colors),
                    // set gamestate to `MOVE_GREY`
                    selectedGrayPiece = selectedGrayPiece,
                    selectingGrayPiece = selectedGrayPiece == null
                )

                if (selectedGrayPiece == null) {
                    logger.info { "selected gray piece: ${selectedGrayPiece?.id}" }
                }

                // TODO: update GUI somehow ?
//                            updatePiecesAtPos(null)
            }
        }

        fun WithMutableState.forceMovePlayerPiece(player: PlayerState) {
            with(nextState) {
                if (selectingGrayPiece || selectingGrayPiece) return
                val playerPieces =
                    figures.filterIsInstance<Piece.Player>().filter { it.playerId == player.id }
                for (playerPiece in playerPieces) {
                    val field = playerPiece.position as? Goal ?: continue
                    if (field.pentaColor != playerPiece.pentaColor) continue

                    // TODO: use extra move type to signal forced move ?

                    // TODO: trigger next action
                    processMove(
                        PentaMove.ForcedPlayerMove(
                            playerPiece = playerPiece,
                            from = field,
                            to = field
                        )
                    )
                }
            }
        }

        fun WithMutableState.checkWin() = with(nextState) {
            // check win after last players turn
            if (winner != null || turn % players.size != players.size - 1) return
            if (selectingGrayPiece || selectedGrayPiece != null || selectedBlackPiece != null) return
            val winners = players.filter { player ->
                val playerPieces =
                    figures.filterIsInstance<Piece.Player>().filter { it.playerId == player.id }
                val offBoardPieces = playerPieces.filter { it.position == null }

                offBoardPieces.size >= 3
            }
            if (winners.isNotEmpty()) {
                nextState = nextState.copy(
                    winner = winners.joinToString(", ") { it.id }
                )
                processMove(PentaMove.Win(winners.map { it.id }))
            }
        }
    }

    fun canMove(start: Field, end: Field): Boolean {
        val backtrack = mutableSetOf<Field>()
        val next = mutableSetOf<Field>(start)
        while (next.isNotEmpty()) {
            val toIterate = next.toList()
            next.clear()
            toIterate.map { nextField ->
                logger.debug { "checking: ${nextField.id}" }
                nextField.connected.forEach { connectedField ->
                    if (connectedField == end) return true
                    if (connectedField in backtrack) return@forEach

                    val free = positions.filterValues { field ->
                        field == connectedField
                    }.isEmpty()
                    if (!free) return@forEach

                    if (connectedField !in backtrack) {
                        backtrack += connectedField
                        next += connectedField
                    }
                }
            }
        }

        return false
    }
}

