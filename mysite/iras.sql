SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for iras_t_administrator
-- ----------------------------
DROP TABLE IF EXISTS `iras_t_administrator`;
CREATE TABLE `iras_t_administrator`  (
  `adminId` int(0) NOT NULL AUTO_INCREMENT,
  `adminAccount` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  PRIMARY KEY (`adminId`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for iras_t_interpretedresume
-- ----------------------------
DROP TABLE IF EXISTS `iras_t_interpretedresume`;
CREATE TABLE `iras_t_interpretedresume`  (
  `interpretedresumecode` int(0) NOT NULL AUTO_INCREMENT,
  `resumecode` int(0) NOT NULL,
  `resumename` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `age` int(0) NULL DEFAULT NULL,
  `degree` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `school` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `address` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `years` int(0) NULL DEFAULT NULL,
  PRIMARY KEY (`interpretedresumecode`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for iras_t_recruiter
-- ----------------------------
DROP TABLE IF EXISTS `iras_t_recruiter`;
CREATE TABLE `iras_t_recruiter`  (
  `recruiterId` int(0) NOT NULL AUTO_INCREMENT,
  `recruiterAccount` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  PRIMARY KEY (`recruiterId`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for iras_t_resume
-- ----------------------------
DROP TABLE IF EXISTS `iras_t_resume`;
CREATE TABLE `iras_t_resume`  (
  `resumecode` int(0) NOT NULL AUTO_INCREMENT,
  `resumename` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `resumeurl` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `recruiterId` int(0) NULL DEFAULT NULL,
  PRIMARY KEY (`resumecode`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
