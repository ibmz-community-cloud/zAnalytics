package com.ibm.scalademo

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.DataFrameWriter
import org.apache.spark.sql.functions._
import org.apache.spark.sql.SaveMode

import java.util.Properties;

object ClientJoinVSAM {
  def main(args: Array[String]){
     val spark = SparkSession
                 .builder()
                 .appName("Scala Test")
                 .getOrCreate()
     if (args.length !=2 ){
       println("Arguments mismatch, username/password is mandatory")
     }
     
     val username = args(0)
     val password = args(1)
     val vsam_string = "jdbc:rs:dv://127.0.0.1:1200; DBTY=DVS; SUBSYS=NONE; UID=" + username + "; PWD=" + password
     val db2_string = "jdbc:rs:dv://127.0.0.1:1200; DBTY=DB2; SUBSYS=DBBG; UID=" + username + "; PWD=" + password
     import spark.implicits._
     
     val clientIncome_df = spark.read
                           .format("jdbc")
                           .option("driver", "com.rs.jdbc.dv.DvDriver")
                           .option("url", vsam_string)
                           .option("dbtable", "vsam_client")
                           .load()
     clientIncome_df.show()
     val clientTrans_df = spark.read
                           .format("jdbc")
                           .option("driver", "com.rs.jdbc.dv.DvDriver")
                           .option("url", db2_string)
                           .option("dbtable", "sparkdb.sppaytb1")
                           .load()
     clientTrans_df.show()
    /**************************************************************/ 
    /* Tailored summary where                                     */ 
    /*   input  is clientTrans_df DataFrame                       */ 
    /*   output is calcTrans_df DataFrame                         */ 
    /**************************************************************/ 
        val calcTrans_df = clientTrans_df.groupBy("CONT_ID").agg( 
        sum("ACAUREQ_AUREQ_TX_DT_TTLAMT").cast("float").as("total_txn_amount"),
        (count("ACAUREQ_AUREQ_TX_DT_TTLAMT")/365).cast("float").as("avg_daily_txns"),
        count("ACAUREQ_AUREQ_TX_DT_TTLAMT").cast("int").as("total_txns"), 
        (sum("ACAUREQ_AUREQ_TX_DT_TTLAMT")/count("ACAUREQ_AUREQ_TX_DT_TTLAMT")).cast("float").as("avg_txn_amount")
    ) 
    /**************************************************************/ 
    /* New client_df DataFrame populated from                     */ 
    /*   clientIncome_df DataFrame                                */ 
    /*    joined with calcTrans_df DataFrame results              */ 
    /**************************************************************/ 
    val client_df = clientIncome_df.select( 
        $"CONT_ID".cast("int").as("customer_id"), 
        $"GENDER".cast("int").as("gender"), 
        $"AGE_YEARS".cast("float").as("age_years"), 
        $"HIGHEST_EDU".cast("int").as("highest_edu"), 
        $"ANNUAL_INVEST".cast("float").as("annual_investment_rev"), 
        $"ANNUAL_INCOME".cast("float").as("annual_income"), 
        $"ACTIVITY_LEVEL".cast("int").as("activity_level"),
        $"CHURN".cast("int").as("churn") 
        ).join(calcTrans_df.select( 
        $"CONT_ID", 
        $"total_txn_amount", 
        $"avg_daily_txns", 
        $"total_txns", 
        $"avg_txn_amount" 
        ), $"CONT_ID" === $"customer_id", "inner") 
    /*******************************************************************/ 
    /* Show first 20 lines (20 clients) from the derived client_df     */ 
    /*   DataFrame                                                     */ 
    /*******************************************************************/ 
    client_df.show()  
    client_df.count()
    
/*    client_df.write
    .mode(SaveMode.Overwrite)
    .format("jdbc")
    .option("driver", "com.rs.jdbc.dv.DvDriver")
    .option("url", "jdbc:rs:dv://172.29.130.234:1200; DBTY=DB2; SUBSYS=DBBG; UID=spkid2; PWD=spkid2; LogConfiguration=file:///home/spkid2/DSDriver/log4j2.xml;")
    .option("dbtable", "spkid2.client_join")
    .save();
*/  
    
     val connectionProperties = new Properties();
     connectionProperties.put("user", username);
     connectionProperties.put("password",password);
     connectionProperties.put("driver", "com.rs.jdbc.dv.DvDriver");
     client_df.write.mode(SaveMode.Overwrite).jdbc(db2_string,"client_join",connectionProperties);  
     println("Data has been written into table client_join")
     }
}