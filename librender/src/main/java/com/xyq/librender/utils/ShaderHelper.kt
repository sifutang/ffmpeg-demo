package com.xyq.librender.utils

import android.opengl.GLES30
import android.util.Log

class ShaderHelper {

    companion object {
        private const val TAG = "ShaderHelper"
        private const val DEBUG = true

        fun buildProgram(vertexShaderSource: String, fragmentShaderSource: String): Int {
            // Compile the shader
            val vertexShader = compileVertexShader(vertexShaderSource)
            val fragmentShader = compileFragmentShader(fragmentShaderSource)

            // Link them into a shader program.
            val program = linkProgram(vertexShader, fragmentShader)
            if (DEBUG) {
                validateProgram(program)
            }

            return program
        }

        private fun compileVertexShader(shaderCode: String): Int {
            return compileShader(GLES30.GL_VERTEX_SHADER, shaderCode)
        }

        private fun compileFragmentShader(shaderCode: String): Int {
            return compileShader(GLES30.GL_FRAGMENT_SHADER, shaderCode)
        }

        private fun compileShader(type:Int, shaderCode: String): Int {
            // 1. create shader
            val shaderObjectId = GLES30.glCreateShader(type)
            if (shaderObjectId == 0) {
                if (DEBUG) {
                    Log.w(TAG, "compileShader: Could not create new shader")
                }

                return 0;
            }

            // 2. upload source to shader
            GLES30.glShaderSource(shaderObjectId, shaderCode)

            // 3. compile shader
            GLES30.glCompileShader(shaderObjectId)

            val compileStatus = intArrayOf(0)
            GLES30.glGetShaderiv(shaderObjectId, GLES30.GL_COMPILE_STATUS, compileStatus, 0)

            if (DEBUG) {
                Log.i(
                    TAG, "compileShader: Results of compiling source:\n $shaderCode \n "
                        + GLES30.glGetShaderInfoLog(shaderObjectId))
            }

            if (compileStatus[0] == 0) {
                // If it failed, delete the shader object.
                GLES30.glDeleteShader(shaderObjectId)

                if (DEBUG) {
                    Log.w(TAG, "compileShader: Compilation of shader failed");
                }

                return 0;
            }

            return shaderObjectId
        }

        private fun linkProgram(vertexShaderId: Int, fragmentShaderId: Int): Int {
            // 1. create program
            val programObjectId = GLES30.glCreateProgram()
            if (programObjectId == 0) {
                if (DEBUG) {
                    Log.w(TAG, "linkProgram: Could not create new program")
                }

                return 0
            }

            GLES30.glAttachShader(programObjectId, vertexShaderId)
            GLES30.glAttachShader(programObjectId, fragmentShaderId)
            GLES30.glLinkProgram(programObjectId)

            val linkStatus = intArrayOf(0)
            GLES30.glGetProgramiv(programObjectId, GLES30.GL_LINK_STATUS, linkStatus, 0)

            if (DEBUG) {
                Log.i(
                    TAG, "linkProgram: Results of linking program:\n"
                            + GLES30.glGetProgramInfoLog(programObjectId)
                )
            }

            if (linkStatus[0] == 0) {
                GLES30.glDeleteProgram(programObjectId)
                if (DEBUG) {
                    Log.w(TAG, "linkProgram: failed")
                }

                return 0
            }

            return programObjectId
        }

        private fun validateProgram(programObjectId: Int): Boolean {
            GLES30.glValidateProgram(programObjectId)

            val validateStatus = intArrayOf(0)
            GLES30.glGetProgramiv(programObjectId, GLES30.GL_VALIDATE_STATUS, validateStatus, 0)
            Log.i(
                TAG, "validateProgram: Results of validating program: " + validateStatus[0]
                    + "\nLog: " + GLES30.glGetProgramInfoLog(programObjectId))

            return validateStatus[0] != 0;
        }
    }
}