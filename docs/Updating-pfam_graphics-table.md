<<<<<<< HEAD
# Updating pfam_graphics table

Updating the pfam_graphics table is easy using the scripts provided in cBioPortal. There is no need to download any file previously.

1- Empty the table `pfam_graphics` (the scripts raise an error if one of the domains that we want to add is already in the table). 
=======
Updating the pfam_graphics table is easy using the scripts provided in cBioPortal. There is no need to download any file previously.

1. Empty the table `pfam_graphics` (the scripts raise an error if one of the domains that we want to add is already in the table).  
>>>>>>> mouse_master
```sql
TRUNCATE TABLE pfam_graphics;
```

<<<<<<< HEAD
2- Run the script `FetchPfamGraphics.java` with the file path of the output file and its name as an argument. This function first searches for all the proteins from mouse that have been reviewed in UniProtKB, then tries to retrieve their Pfam domains, and finally saves the information in a file. This script can also be run in the command line by going into  `<your_cbioportal_folder>/core/src/main/scripts/` and typing:
=======
2. Run the script `FetchPfamGraphics.java` with the file path of the output file and its name as an argument. This function first searches for all the proteins from mouse that have been reviewed in UniProtKB, then tries to retrieve their Pfam domains, and finally saves the information in a file. This script can also be run in the command line by going into  `<your_cbioportal_folder>/core/src/main/scripts/` and typing:
>>>>>>> mouse_master
```
fetchPfamGraphicsData.sh <output_pfam_mapping_file>
```

<<<<<<< HEAD
3- Run the script `ImportPfamGraphics.java`, which uses the output of the previous script as an input to write all the information retrieved to the database. This script can also be run in the command line by typing:
=======
3. Run the script `ImportPfamGraphics.java`, which uses the output of the previous script as an input to write all the information retrieved to the database. This script can also be run in the command line by typing:
>>>>>>> mouse_master
```
importPfamGraphics.pl <pfam_mapping_file>
```